package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.dto.QuestaoComAlternativasDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/** FE-202: banco de questões (acervo das questões já criadas, com busca, filtros e reaproveitamento). */
@Service
public class BancoQuestoesService {

    public record Item(Integer id, String tipo, String cabecalho, String enunciado, List<String> alternativas,
                       Integer gabarito, String assunto, String explicacao, String rubrica, String dificuldade,
                       List<String> tags, UUID listaId, String listaTitulo, UUID disciplinaId, String disciplinaNome,
                       List<QuestaoComAlternativasDTO.ImagemDTO> imagens) {}

    public record Pagina(List<Item> items, int page, int size, long totalElements, int totalPages) {}

    public record Opcao(UUID id, String nome) {}

    public record Filtros(List<String> assuntos, List<String> tags, List<Opcao> disciplinas) {}

    public record ResultadoLote(int adicionadas, int ignoradas, UUID listaId) {}

    @Autowired
    private QuestaoRepositores questaoRepositores;
    @Autowired
    private ListaRepository listaRepository;
    @Autowired
    private QuestaoCloneService cloneService;
    @Autowired
    private QuestaoOtimizadoService questaoOtimizadoService;

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    /** Questões visíveis ao chamador: as das listas do professor (null = instituição/admin vê todas). */
    private List<Questao> doEscopo(UUID professorId) {
        return questaoRepositores.findAllComLista().stream()
                .filter(q -> q.getLista() != null)
                .filter(q -> professorId == null
                        || (q.getLista().getProfessor() != null && professorId.equals(q.getLista().getProfessor().getId())))
                .collect(Collectors.toList());
    }

    private Item paraItem(Questao q, boolean comImagens) {
        Lista l = q.getLista();
        Disciplina d = l.getDisciplina();
        List<QuestaoComAlternativasDTO.ImagemDTO> imagens = comImagens
                ? questaoOtimizadoService.buscarQuestaoComAlternativasPorId(q.getId())
                        .map(QuestaoComAlternativasDTO::getImagens).orElse(List.of())
                : List.of();
        return new Item(q.getId(), q.isAberta() ? "ABERTA" : "OBJETIVA", q.getCabecalho(), q.getEnunciado(),
                q.getAlternativasTexto(), q.getGabarito(), q.getAssunto(), q.getExplicacao(), q.getRubrica(),
                q.getDificuldade(), new ArrayList<>(q.getTags()), l.getId(), l.getTitulo(),
                d == null ? null : d.getId(), d == null ? null : d.getNome(), imagens);
    }

    @Transactional(readOnly = true)
    public Pagina buscar(UUID professorId, String texto, String assunto, String dificuldade, String tag,
                         UUID disciplinaId, String tipo, int page, int size) {
        String t = norm(texto);
        String a = norm(assunto);
        String tg = norm(tag);
        String dif = dificuldade == null ? "" : dificuldade.trim().toUpperCase();
        List<Questao> filtradas = doEscopo(professorId).stream()
                .filter(q -> t.isEmpty() || norm(q.getEnunciado()).contains(t) || norm(q.getCabecalho()).contains(t)
                        || norm(q.getAssunto()).contains(t) || q.getTags().stream().anyMatch(x -> norm(x).contains(t)))
                .filter(q -> a.isEmpty() || norm(q.getAssunto()).equals(a))
                .filter(q -> dif.isEmpty() || dif.equals(q.getDificuldade()))
                .filter(q -> tg.isEmpty() || q.getTags().stream().anyMatch(x -> norm(x).equals(tg)))
                .filter(q -> disciplinaId == null || (q.getLista().getDisciplina() != null
                        && disciplinaId.equals(q.getLista().getDisciplina().getId())))
                .filter(q -> tipo == null || tipo.isBlank() || tipo.equalsIgnoreCase(q.isAberta() ? "ABERTA" : "OBJETIVA"))
                .sorted(Comparator.comparing(Questao::getId).reversed())
                .collect(Collectors.toList());

        int safeSize = Math.min(Math.max(size, 1), 50);
        int safePage = Math.max(page, 0);
        int from = Math.min(safePage * safeSize, filtradas.size());
        int to = Math.min(from + safeSize, filtradas.size());
        List<Item> items = filtradas.subList(from, to).stream().map(q -> paraItem(q, false)).collect(Collectors.toList());
        int totalPages = (int) Math.ceil(filtradas.size() / (double) safeSize);
        return new Pagina(items, safePage, safeSize, filtradas.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public Filtros filtros(UUID professorId) {
        List<Questao> todas = doEscopo(professorId);
        TreeSet<String> assuntos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> tags = new TreeSet<>();
        Map<UUID, Opcao> disciplinas = new LinkedHashMap<>();
        for (Questao q : todas) {
            if (q.getAssunto() != null && !q.getAssunto().isBlank()) assuntos.add(q.getAssunto().trim());
            tags.addAll(q.getTags());
            Disciplina d = q.getLista().getDisciplina();
            if (d != null) disciplinas.putIfAbsent(d.getId(), new Opcao(d.getId(), d.getNome()));
        }
        return new Filtros(new ArrayList<>(assuntos), new ArrayList<>(tags), new ArrayList<>(disciplinas.values()));
    }

    private Questao carregar(Integer id, UUID professorId) {
        Questao q = questaoRepositores.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão não encontrada"));
        if (q.getLista() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão sem lista");
        if (professorId != null && (q.getLista().getProfessor() == null
                || !professorId.equals(q.getLista().getProfessor().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta questão pertence a outro professor");
        }
        return q;
    }

    @Transactional(readOnly = true)
    public Item detalhe(Integer id, UUID professorId) {
        return paraItem(carregar(id, professorId), true);
    }

    /** Edita só a classificação (tags, dificuldade e assunto); o enunciado não muda por aqui. */
    @Transactional
    public Item classificar(Integer id, UUID professorId, Map<String, Object> corpo) {
        Questao q = carregar(id, professorId);
        if (corpo.containsKey("tags")) {
            Object raw = corpo.get("tags");
            List<String> tags = new ArrayList<>();
            if (raw instanceof Collection<?> c) c.forEach(x -> tags.add(String.valueOf(x)));
            q.setTags(QuestaoService.normalizarTags(tags));
        }
        if (corpo.containsKey("dificuldade")) {
            Object d = corpo.get("dificuldade");
            q.setDificuldade(QuestaoService.normalizarDificuldade(d == null ? null : String.valueOf(d)));
        }
        if (corpo.containsKey("assunto")) {
            Object s = corpo.get("assunto");
            String v = s == null ? null : String.valueOf(s).trim();
            q.setAssunto(v == null || v.isEmpty() ? null : v.substring(0, Math.min(v.length(), 255)));
        }
        questaoRepositores.save(q);
        return paraItem(q, false);
    }

    @Transactional
    public ResultadoLote adicionarALista(UUID professorId, UUID listaId, List<Integer> questaoIds) {
        if (questaoIds == null || questaoIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos uma questão");
        }
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lista não encontrada"));
        if (professorId != null && (lista.getProfessor() == null || !professorId.equals(lista.getProfessor().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta lista pertence a outro professor");
        }
        Set<String> existentes = questaoRepositores.findByListaId(listaId).stream()
                .map(q -> norm(q.getEnunciado())).collect(Collectors.toSet());
        int ok = 0;
        int ignoradas = 0;
        for (Integer id : new LinkedHashSet<>(questaoIds)) {
            Questao origem = carregar(id, professorId);
            if (!existentes.add(norm(origem.getEnunciado()))) {
                ignoradas++;
                continue;
            }
            cloneService.clonar(origem, lista);
            ok++;
        }
        return new ResultadoLote(ok, ignoradas, listaId);
    }
}
