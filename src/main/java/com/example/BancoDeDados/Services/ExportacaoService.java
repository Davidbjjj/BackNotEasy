package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/** FE-208: exporta notas em CSV ou PDF. A geração roda em segundo plano e avisa o professor ao terminar. */
@Service
public class ExportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ExportacaoService.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Sao_Paulo"));

    /** Colunas disponíveis, na ordem em que aparecem no arquivo. chave -> título. */
    public static final LinkedHashMap<String, String> COLUNAS = new LinkedHashMap<>();
    static {
        COLUNAS.put("aluno", "Aluno");
        COLUNAS.put("email", "E-mail");
        COLUNAS.put("disciplina", "Disciplina");
        COLUNAS.put("lista", "Lista");
        COLUNAS.put("nota", "Nota");
        COLUNAS.put("porcentagem", "% de acertos");
        COLUNAS.put("acertos", "Acertos");
        COLUNAS.put("totalQuestoes", "Questões");
        COLUNAS.put("status", "Situação");
        COLUNAS.put("entregueEm", "Entregue em");
        COLUNAS.put("tentativas", "Tentativas");
    }

    public record ExportarRequest(String escopo, UUID alvoId, String formato, List<String> colunas) {}

    public record Status(UUID id, String escopo, String alvoNome, String formato, String status, String nomeArquivo,
                         String erro, Instant criadaEm, Instant concluidaEm) {}

    public record Arquivo(String nome, String contentType, byte[] dados) {}

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Autowired
    private ExportacaoRepository repository;
    @Autowired
    private ListaRepository listaRepository;
    @Autowired
    private DisciplinaRepository disciplinaRepository;
    @Autowired
    private ListaEstudanteNotaRepository notaRepository;
    @Autowired
    private NotificacaoService notificacaoService;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private static Status paraStatus(Exportacao e) {
        return new Status(e.getId(), e.getEscopo(), e.getAlvoNome(), e.getFormato(), e.getStatus(), e.getNomeArquivo(),
                e.getErro(), e.getCriadaEm(), e.getConcluidaEm());
    }

    public Status criar(UUID professorId, UUID donoId, ExportarRequest req) {
        String escopo = req.escopo() == null ? "" : req.escopo().toUpperCase();
        String formato = req.formato() == null ? "" : req.formato().toUpperCase();
        if (!escopo.equals("LISTA") && !escopo.equals("DISCIPLINA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escopo inválido: use LISTA ou DISCIPLINA");
        }
        if (!formato.equals("CSV") && !formato.equals("PDF")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido: use CSV ou PDF");
        }
        if (req.alvoId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha a lista ou a disciplina");
        List<String> cols = req.colunas() == null || req.colunas().isEmpty()
                ? new ArrayList<>(COLUNAS.keySet())
                : COLUNAS.keySet().stream().filter(req.colunas()::contains).collect(Collectors.toList());
        if (cols.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha ao menos uma coluna válida");

        String nome;
        if (escopo.equals("LISTA")) {
            Lista l = listaRepository.findById(req.alvoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lista não encontrada"));
            if (professorId != null && (l.getProfessor() == null || !professorId.equals(l.getProfessor().getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta lista pertence a outro professor");
            }
            nome = l.getTitulo();
        } else {
            Disciplina d = disciplinaRepository.findById(req.alvoId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Disciplina não encontrada"));
            if (professorId != null && (d.getProfessor() == null || !professorId.equals(d.getProfessor().getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta disciplina pertence a outro professor");
            }
            nome = d.getNome();
        }

        Exportacao e = new Exportacao();
        e.setProfessorId(donoId);
        e.setEscopo(escopo);
        e.setAlvoId(req.alvoId());
        e.setAlvoNome(nome);
        e.setFormato(formato);
        e.setColunas(String.join(",", cols));
        e = repository.save(e); // commit antes de iniciar a tarefa
        UUID id = e.getId();
        CompletableFuture.runAsync(() -> gerar(id), executor);
        return paraStatus(e);
    }

    private void gerar(UUID id) {
        Exportacao e = repository.findById(id).orElse(null);
        if (e == null) return;
        try {
            List<String> cols = Arrays.asList(e.getColunas().split(","));
            List<Map<String, String>> linhas = new TransactionTemplate(transactionManager)
                    .execute(s -> coletar(e.getEscopo(), e.getAlvoId()));
            byte[] dados = e.getFormato().equals("CSV") ? csv(cols, linhas) : pdf(e.getAlvoNome(), cols, linhas);
            e.setDados(dados);
            e.setNomeArquivo("notas-" + slug(e.getAlvoNome()) + "." + e.getFormato().toLowerCase());
            e.setStatus("PRONTA");
        } catch (Exception ex) {
            log.error("Falha ao gerar exportação {}", id, ex);
            e.setStatus("ERRO");
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            e.setErro(msg.length() > 280 ? msg.substring(0, 280) : msg);
        }
        e.setConcluidaEm(Instant.now());
        repository.save(e);
        if ("PRONTA".equals(e.getStatus())) {
            notificacaoService.criar(e.getProfessorId(), TipoNotificacao.GERAL, "Exportação pronta",
                    "O arquivo de notas de " + e.getAlvoNome() + " (" + e.getFormato() + ") está pronto para baixar.",
                    "/exportar-notas");
        }
    }

    private List<Map<String, String>> coletar(String escopo, UUID alvoId) {
        List<Lista> listas = escopo.equals("LISTA")
                ? listaRepository.findById(alvoId).map(List::of).orElse(List.of())
                : listaRepository.findByDisciplinaId(alvoId).stream().filter(l -> !l.isRascunho()).collect(Collectors.toList());
        List<Map<String, String>> out = new ArrayList<>();
        for (Lista l : listas) {
            Map<UUID, ListaEstudanteNota> notas = notaRepository.findByListaId(l.getId()).stream()
                    .collect(Collectors.toMap(n -> n.getEstudante().getId(), n -> n, (a, b) -> a));
            Instant prazo = AlunosRiscoService.prazoDe(l);
            List<Estudante> alunos = new ArrayList<>(l.getEstudantes() == null ? List.of() : l.getEstudantes());
            alunos.sort(Comparator.comparing(Estudante::getNome, String.CASE_INSENSITIVE_ORDER));
            for (Estudante a : alunos) {
                ListaEstudanteNota n = notas.get(a.getId());
                boolean entregue = n != null && n.isFinalizada();
                String status = entregue ? "Entregue"
                        : (prazo != null && prazo.isBefore(Instant.now()) ? "Atrasada" : "Não feita");
                Map<String, String> m = new HashMap<>();
                m.put("aluno", a.getNome());
                m.put("email", a.getEmail());
                m.put("disciplina", l.getDisciplina() == null ? "" : l.getDisciplina().getNome());
                m.put("lista", l.getTitulo());
                m.put("nota", n == null || n.getNota() == null || !entregue ? "" : num(n.getNota().doubleValue()));
                m.put("porcentagem", n == null || n.getPorcentagemAcertos() == null || !entregue ? ""
                        : num(n.getPorcentagemAcertos().doubleValue()));
                m.put("acertos", n == null || n.getQuestoesCorretas() == null || !entregue ? "" : String.valueOf(n.getQuestoesCorretas()));
                m.put("totalQuestoes", n == null || n.getTotalQuestoes() == null ? "" : String.valueOf(n.getTotalQuestoes()));
                m.put("status", status);
                m.put("entregueEm", n == null || n.getFinalizadaEm() == null ? "" : DATA.format(n.getFinalizadaEm()));
                m.put("tentativas", n == null || n.getTentativas() == null ? "0" : String.valueOf(n.getTentativas()));
                out.add(m);
            }
        }
        return out;
    }

    private static String num(double v) {
        return String.format(Locale.forLanguageTag("pt-BR"), "%.2f", v);
    }

    private static String slug(String s) {
        String v = Normalizer.normalize(s == null ? "arquivo" : s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return v.isEmpty() ? "arquivo" : v.substring(0, Math.min(v.length(), 60));
    }

    /** CSV com BOM e ponto e vírgula (abre direto no Excel em português). Neutraliza fórmulas (=, +, -, @). */
    static byte[] csv(List<String> cols, List<Map<String, String>> linhas) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(cols.stream().map(c -> campo(COLUNAS.get(c))).collect(Collectors.joining(";"))).append("\r\n");
        for (Map<String, String> l : linhas) {
            sb.append(cols.stream().map(c -> campo(l.getOrDefault(c, ""))).collect(Collectors.joining(";"))).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String campo(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?[0-9]+([.,][0-9]+)?")) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    static byte[] pdf(String titulo, List<String> cols, List<Map<String, String>> linhas) throws java.io.IOException {
        PDRectangle pagina = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()); // paisagem
        float margem = 36;
        float largura = pagina.getWidth() - 2 * margem;
        float colW = largura / cols.size();
        int maxChars = Math.max(6, (int) (colW / 4.2f));
        float alturaLinha = 14;

        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = null;
            PDPageContentStream cs = null;
            float y = 0;
            int n = 0;
            int idx = 0;
            do {
                if (cs == null || y < margem + alturaLinha) {
                    if (cs != null) cs.close();
                    page = new PDPage(pagina);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pagina.getHeight() - margem;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 13);
                    cs.newLineAtOffset(margem, y);
                    cs.showText(limpar("Notas - " + titulo, 90));
                    cs.endText();
                    y -= 22;
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 8);
                    linha(cs, margem, y, colW, cols.stream().map(COLUNAS::get).collect(Collectors.toList()), maxChars);
                    y -= alturaLinha;
                    cs.setFont(PDType1Font.HELVETICA, 8);
                }
                if (idx < linhas.size()) {
                    Map<String, String> l = linhas.get(idx);
                    linha(cs, margem, y, colW, cols.stream().map(c -> l.getOrDefault(c, "")).collect(Collectors.toList()), maxChars);
                    y -= alturaLinha;
                }
                idx++;
                n++;
            } while (idx < linhas.size());
            if (linhas.isEmpty()) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(margem, y);
                cs.showText("Nenhum aluno encontrado.");
                cs.endText();
            }
            cs.close();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static void linha(PDPageContentStream cs, float x0, float y, float colW, List<String> valores, int maxChars)
            throws java.io.IOException {
        float x = x0;
        for (String v : valores) {
            cs.beginText();
            cs.newLineAtOffset(x, y);
            cs.showText(limpar(v, maxChars));
            cs.endText();
            x += colW;
        }
    }

    /** Mantém só caracteres que a fonte padrão do PDF (WinAnsi) consegue desenhar, e corta o excesso. */
    private static String limpar(String v, int max) {
        String s = v == null ? "" : v;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            sb.append(c >= 32 && c <= 255 && c != 127 ? c : '?');
        }
        String t = sb.toString();
        return t.length() > max ? t.substring(0, Math.max(1, max - 1)) + "." : t;
    }

    public List<Status> listar(UUID donoId) {
        return repository.findTop20ByProfessorIdOrderByCriadaEmDesc(donoId).stream()
                .map(ExportacaoService::paraStatus).collect(Collectors.toList());
    }

    private Exportacao doDono(UUID id, UUID donoId) {
        Exportacao e = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exportação não encontrada"));
        if (!e.getProfessorId().equals(donoId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta exportação é de outro usuário");
        }
        return e;
    }

    public Status status(UUID id, UUID donoId) {
        return paraStatus(doDono(id, donoId));
    }

    public Arquivo arquivo(UUID id, UUID donoId) {
        Exportacao e = doDono(id, donoId);
        if (!"PRONTA".equals(e.getStatus()) || e.getDados() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O arquivo ainda não está pronto");
        }
        return new Arquivo(e.getNomeArquivo(), e.getFormato().equals("CSV") ? "text/csv; charset=UTF-8" : "application/pdf",
                e.getDados());
    }
}
