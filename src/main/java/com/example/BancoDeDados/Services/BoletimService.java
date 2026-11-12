package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/** FE-308: boletim por aluno/turma e relatório de risco e evasão, em PDF (e CSV no caso do relatório). */
@Service
public class BoletimService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZONA);

    public record Arquivo(String nome, String contentType, byte[] dados) {}

    private record LinhaBoletim(String disciplina, String professor, String media, int entregues, int total, int atrasadas) {}

    @Autowired private TurmaRepository turmaRepository;
    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private ListaRepository listaRepository;
    @Autowired private ListaEstudanteNotaRepository notaRepository;
    @Autowired private ConfiguracaoInstituicaoService configuracao;
    @Autowired private AlunosRiscoService risco;
    @Autowired private AuditoriaService auditoria;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    private Turma turma(UUID instituicaoId, UUID id) {
        Turma t = turmaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Turma não encontrada"));
        if (!t.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Turma de outra instituição");
        return t;
    }

    private byte[] logo(UUID instituicaoId) {
        return configuracao.logo(instituicaoId).map(ConfiguracaoInstituicaoService.Logo::dados).orElse(null);
    }

    private static String slug(String s) {
        String v = java.text.Normalizer.normalize(s == null ? "arquivo" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return v.isEmpty() ? "arquivo" : v;
    }

    private List<LinhaBoletim> linhas(Estudante e, Collection<Disciplina> ds, Periodo periodo) {
        Instant a = periodo.getInicio().atStartOfDay(ZONA).toInstant();
        Instant b = periodo.getFim().plusDays(1).atStartOfDay(ZONA).toInstant();
        Map<UUID, ListaEstudanteNota> notas = notaRepository.findByEstudanteId(e.getId()).stream()
                .collect(Collectors.toMap(n -> n.getLista().getId(), n -> n, (x, y) -> x));
        List<LinhaBoletim> out = new ArrayList<>();
        for (Disciplina d : ds) {
            double soma = 0;
            int comNota = 0, entregues = 0, total = 0, atrasadas = 0;
            for (Lista l : listaRepository.findByDisciplinaId(d.getId())) {
                if (l.isRascunho() || l.getEstudantes() == null || l.getEstudantes().stream().noneMatch(x -> x.getId().equals(e.getId()))) continue;
                total++;
                ListaEstudanteNota n = notas.get(l.getId());
                Instant prazo = AlunosRiscoService.prazoDe(l);
                if (n != null && n.isFinalizada() && n.getFinalizadaEm() != null
                        && !n.getFinalizadaEm().isBefore(a) && n.getFinalizadaEm().isBefore(b)) {
                    entregues++;
                    if (n.getNota() != null) { soma += n.getNota().doubleValue(); comNota++; }
                } else if ((n == null || !n.isFinalizada()) && prazo != null && prazo.isBefore(Instant.now())) {
                    atrasadas++;
                }
            }
            out.add(new LinhaBoletim(d.getNome(), d.getProfessor() == null ? "" : d.getProfessor().getNome(),
                    comNota == 0 ? "—" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", soma / comNota), entregues, total, atrasadas));
        }
        return out;
    }

    /** Uma página de boletim. */
    private void pagina(PDDocument doc, Instituicao inst, byte[] logo, Estudante e, Periodo periodo, Turma turma,
                        List<LinhaBoletim> linhas) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        float m = 48;
        float y = page.getMediaBox().getHeight() - m;
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float lw = ArquivosUtil.desenharLogo(doc, cs, logo, m, y, 48);
            float x = m + (lw > 0 ? lw + 14 : 0);
            ArquivosUtil.texto(cs, PDType1Font.HELVETICA_BOLD, 16, x, y - 20, inst.getNome());
            ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 11, x, y - 38, "Boletim - " + periodo.getNome()
                    + " (" + periodo.getInicio().format(DATA) + " a " + periodo.getFim().format(DATA) + ")");
            y -= 80;
            ArquivosUtil.texto(cs, PDType1Font.HELVETICA_BOLD, 12, m, y, "Aluno: " + e.getNome());
            ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 10, m, y - 15, "E-mail: " + e.getEmail()
                    + (turma != null ? "   |   Turma: " + turma.getNome() : ""));
            y -= 45;
            float[] cx = {m, m + 190, m + 330, m + 395, m + 455};
            String[] cab = {"Disciplina", "Professor", "Média", "Entregues", "Atrasadas"};
            for (int i = 0; i < cab.length; i++) ArquivosUtil.texto(cs, PDType1Font.HELVETICA_BOLD, 9, cx[i], y, cab[i]);
            cs.moveTo(m, y - 4);
            cs.lineTo(page.getMediaBox().getWidth() - m, y - 4);
            cs.stroke();
            y -= 18;
            for (LinhaBoletim l : linhas) {
                ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 9, cx[0], y, ArquivosUtil.limpar(l.disciplina(), 38));
                ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 9, cx[1], y, ArquivosUtil.limpar(l.professor(), 28));
                ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 9, cx[2], y, l.media());
                ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 9, cx[3], y, l.entregues() + " de " + l.total());
                ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 9, cx[4], y, String.valueOf(l.atrasadas()));
                y -= 16;
            }
            if (linhas.isEmpty()) ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 10, m, y, "Aluno sem disciplinas neste período.");
            ArquivosUtil.texto(cs, PDType1Font.HELVETICA, 8, m, 36, "Média das listas entregues no período (0 a 10). Emitido em "
                    + DATA_HORA.format(Instant.now()) + ".");
        }
    }

    private byte[] salvar(PDDocument doc) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Transactional
    public Arquivo boletimTurma(UUID instituicaoId, UUID turmaId, UUID alunoId) {
        Turma t = turma(instituicaoId, turmaId);
        Instituicao inst = instituicaoRepository.findById(instituicaoId).orElseThrow();
        List<Estudante> alunos = t.getEstudantes().stream()
                .filter(e -> alunoId == null || e.getId().equals(alunoId))
                .sorted(Comparator.comparing(Estudante::getNome, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
        if (alunoId != null && alunos.isEmpty()) throw erro(HttpStatus.NOT_FOUND, "Este aluno não está na turma");
        if (alunos.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "A turma não tem alunos");
        byte[] logo = logo(instituicaoId);
        try (PDDocument doc = new PDDocument()) {
            for (Estudante e : alunos) pagina(doc, inst, logo, e, t.getPeriodo(), t, linhas(e, t.getDisciplinas(), t.getPeriodo()));
            auditoria.registrar(instituicaoId, "BOLETIM_EMITIDO", "Turma", t.getId(),
                    alunoId == null ? "Boletins da turma " + t.getNome() + " (" + alunos.size() + " alunos)"
                            : "Boletim de " + alunos.get(0).getNome() + " (" + t.getNome() + ")", null, null);
            String nome = alunoId == null ? "boletins-" + slug(t.getNome()) : "boletim-" + slug(alunos.get(0).getNome());
            return new Arquivo(nome + ".pdf", "application/pdf", salvar(doc));
        } catch (IOException ex) {
            throw erro(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível gerar o PDF");
        }
    }

    /** Boletim de um aluno em um período, usando as turmas desse período em que ele está. */
    @Transactional
    public Arquivo boletimAluno(UUID instituicaoId, UUID estudanteId, UUID periodoId) {
        Periodo p = periodoRepository.findById(periodoId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Período não encontrado"));
        if (!p.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Período de outra instituição");
        Estudante e = estudanteRepository.findById(estudanteId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aluno não encontrado"));
        Instituicao inst = instituicaoRepository.findById(instituicaoId).orElseThrow();
        if (!inst.getNome().equals(e.getInstituicao())) throw erro(HttpStatus.FORBIDDEN, "Aluno de outra instituição");
        Turma t = turmaRepository.findByPeriodoIdOrderByNomeAsc(periodoId).stream()
                .filter(x -> x.getEstudantes().stream().anyMatch(a -> a.getId().equals(estudanteId))).findFirst()
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "O aluno não está em nenhuma turma deste período"));
        return boletimTurma(instituicaoId, t.getId(), estudanteId);
    }

    /** Relatório de risco e evasão de uma turma, em PDF ou CSV. */
    @Transactional
    public Arquivo riscoTurma(UUID instituicaoId, UUID turmaId, String formato) {
        Turma t = turma(instituicaoId, turmaId);
        Instituicao inst = instituicaoRepository.findById(instituicaoId).orElseThrow();
        Set<UUID> alunos = t.getEstudantes().stream().map(Estudante::getId).collect(Collectors.toSet());
        var painel = risco.painelDe(new ArrayList<>(t.getDisciplinas()), alunos);
        List<String> cab = List.of("Aluno", "E-mail", "Média", "Último acesso", "Listas atrasadas", "Motivos", "Situação");
        List<List<String>> linhas = new ArrayList<>();
        for (var a : painel.items()) {
            linhas.add(List.of(a.nome(), a.email(), a.media() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f", a.media()),
                    a.ultimoAcesso() == null ? "Nunca acessou" : DATA_HORA.format(a.ultimoAcesso()),
                    String.valueOf(a.listasAtrasadas()),
                    a.motivos().stream().map(m -> switch (m) {
                        case "MEDIA_BAIXA" -> "Média baixa";
                        case "SEM_ACESSO" -> "Sem acesso";
                        case "LISTAS_ATRASADAS" -> "Listas atrasadas";
                        default -> m;
                    }).collect(Collectors.joining(", ")),
                    switch (a.nivel()) { case "ALTO" -> "Risco alto"; case "MEDIO" -> "Risco médio"; default -> "Sem risco"; }));
        }
        String f = formato == null ? "pdf" : formato.toLowerCase();
        String base = "risco-evasao-" + slug(t.getNome());
        try {
            auditoria.registrar(instituicaoId, "RELATORIO_RISCO_EMITIDO", "Turma", t.getId(), "Risco e evasão da turma " + t.getNome(), null, null);
            if ("csv".equals(f)) return new Arquivo(base + ".csv", "text/csv; charset=UTF-8", ArquivosUtil.csv(cab, linhas));
            if (!"pdf".equals(f)) throw erro(HttpStatus.BAD_REQUEST, "Formato inválido: use pdf ou csv");
            byte[] pdf = ArquivosUtil.tabelaPdf("Risco e evasão - " + t.getNome(),
                    List.of(inst.getNome() + " | " + t.getPeriodo().getNome(), painel.criterios().regra(),
                            "Gerado em " + DATA_HORA.format(Instant.now())), logo(instituicaoId), cab, linhas);
            return new Arquivo(base + ".pdf", "application/pdf", pdf);
        } catch (IOException ex) {
            throw erro(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível gerar o PDF");
        }
    }
}
