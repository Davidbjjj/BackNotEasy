package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.ListaResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Gestão de listas pelo professor: regras de aplicação (FE-205), duplicar/publicar (FE-206), reabrir e
 * lista de reforço (FE-203/204).
 */
@Service
public class ListaGestaoService {

    public record Configuracao(Instant abreEm, Instant fechaEm, Integer maxTentativas, String regraNota,
                               Integer tempoLimiteMinutos, Boolean embaralharQuestoes, Boolean embaralharAlternativas) {}

    public record DuplicarRequest(UUID disciplinaId, String titulo, Instant abreEm, Instant fechaEm) {}

    public record ReabrirRequest(Instant fechaEm, Boolean permitirNovaTentativa) {}

    public record ReabrirResultado(ListaResponseDTO lista, int notificados) {}

    public record ReforcoRequest(UUID disciplinaId, String titulo, List<String> assuntos, List<UUID> estudanteIds,
                                 Integer quantidadePorAssunto, Boolean publicar, Instant fechaEm) {}

    public record ReforcoResultado(ListaResponseDTO lista, int questoes, int alunos) {}

    public record AssuntoFraco(String assunto, double taxaAcerto, long respostas, long questoes) {}

    @Autowired
    private ListaRepository listaRepository;
    @Autowired
    private DisciplinaRepository disciplinaRepository;
    @Autowired
    private EstudanteRepositores estudanteRepository;
    @Autowired
    private QuestaoRepositores questaoRepository;
    @Autowired
    private RespostaEstudantesRepository respostaRepository;
    @Autowired
    private ListaEstudanteNotaRepository notaRepository;
    @Autowired
    private QuestaoCloneService cloneService;
    @Autowired
    private NotificacaoService notificacaoService;
    @Autowired
    private AuditoriaService auditoria;

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }

    private static ResponseStatusException erro(HttpStatus s, String msg) {
        return new ResponseStatusException(s, msg);
    }

    private Lista carregarLista(UUID id, UUID professorId) {
        Lista l = listaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Lista não encontrada"));
        if (professorId != null && (l.getProfessor() == null || !professorId.equals(l.getProfessor().getId()))) {
            throw erro(HttpStatus.FORBIDDEN, "Esta lista pertence a outro professor");
        }
        return l;
    }

    /** Alterações exigem disciplina ativa (arquivada = só leitura, FE-304). */
    private Lista carregarListaParaEditar(UUID id, UUID professorId) {
        Lista l = carregarLista(id, professorId);
        if (l.getDisciplina() != null) l.getDisciplina().exigirAtiva();
        return l;
    }

    private Disciplina carregarDisciplina(UUID id, UUID professorId) {
        Disciplina d = disciplinaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Disciplina não encontrada"));
        if (professorId != null && (d.getProfessor() == null || !professorId.equals(d.getProfessor().getId()))) {
            throw erro(HttpStatus.FORBIDDEN, "Esta disciplina pertence a outro professor");
        }
        return d;
    }

    private Disciplina carregarDisciplinaAtiva(UUID id, UUID professorId) {
        Disciplina d = carregarDisciplina(id, professorId);
        d.exigirAtiva();
        return d;
    }

    @Transactional(readOnly = true)
    public ListaResponseDTO obter(UUID listaId, UUID professorId) {
        return ListaResponseDTO.de(carregarLista(listaId, professorId));
    }

    // ---------------------------------------------------------------- FE-205

    @Transactional
    public ListaResponseDTO configurar(UUID listaId, UUID professorId, Configuracao c) {
        Lista l = carregarListaParaEditar(listaId, professorId);
        if (c.abreEm() != null && c.fechaEm() != null && !c.fechaEm().isAfter(c.abreEm())) {
            throw erro(HttpStatus.BAD_REQUEST, "O fechamento precisa ser depois da abertura");
        }
        if (c.maxTentativas() != null && (c.maxTentativas() < 1 || c.maxTentativas() > 20)) {
            throw erro(HttpStatus.BAD_REQUEST, "O número de tentativas deve ficar entre 1 e 20");
        }
        String regra = c.regraNota() == null ? "MAIOR" : c.regraNota().toUpperCase();
        if (!regra.equals("MAIOR") && !regra.equals("ULTIMA")) {
            throw erro(HttpStatus.BAD_REQUEST, "Regra da nota inválida: use MAIOR ou ULTIMA");
        }
        if (c.tempoLimiteMinutos() != null && (c.tempoLimiteMinutos() < 1 || c.tempoLimiteMinutos() > 600)) {
            throw erro(HttpStatus.BAD_REQUEST, "O tempo limite deve ficar entre 1 e 600 minutos");
        }
        String antes = "abre=" + l.getAbreEm() + ", fecha=" + l.getFechaEm() + ", tentativas=" + l.getMaxTentativas()
                + ", regra=" + l.getRegraNota() + ", tempo=" + l.getTempoLimiteMinutos();
        l.setAbreEm(c.abreEm());
        l.setFechaEm(c.fechaEm());
        l.setMaxTentativas(c.maxTentativas());
        l.setRegraNota(regra);
        l.setTempoLimiteMinutos(c.tempoLimiteMinutos());
        l.setEmbaralharQuestoes(Boolean.TRUE.equals(c.embaralharQuestoes()));
        l.setEmbaralharAlternativas(Boolean.TRUE.equals(c.embaralharAlternativas()));
        Lista salva = listaRepository.save(l);
        if (l.getProfessor() != null && l.getProfessor().getInstituicao() != null) {
            auditoria.registrar(l.getProfessor().getInstituicao().getId(), "CONFIG_LISTA", "Lista", l.getId(),
                    "Regras da lista \"" + l.getTitulo() + "\" alteradas", antes,
                    "abre=" + l.getAbreEm() + ", fecha=" + l.getFechaEm() + ", tentativas=" + l.getMaxTentativas()
                            + ", regra=" + l.getRegraNota() + ", tempo=" + l.getTempoLimiteMinutos());
        }
        return ListaResponseDTO.de(salva);
    }

    // ---------------------------------------------------------------- FE-206

    /** Publica um rascunho: libera para os alunos da disciplina e avisa que há lista nova. */
    @Transactional
    public ListaResponseDTO publicar(UUID listaId, UUID professorId) {
        Lista l = carregarListaParaEditar(listaId, professorId);
        if (questaoRepository.findByListaId(listaId).isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "A lista precisa ter ao menos uma questão para ser publicada");
        }
        boolean eraRascunho = l.isRascunho();
        l.setStatus("PUBLICADA");
        if (l.getEstudantes() == null) l.setEstudantes(new ArrayList<>());
        if (eraRascunho && l.getEstudantes().isEmpty() && l.getDisciplina() != null) {
            l.getEstudantes().addAll(l.getDisciplina().getEstudantes());
        }
        listaRepository.save(l);
        if (eraRascunho) notificarListaNova(l, l.getEstudantes());
        return ListaResponseDTO.de(l);
    }

    @Transactional
    public ListaResponseDTO duplicar(UUID listaId, UUID professorId, DuplicarRequest req) {
        Lista origem = carregarLista(listaId, professorId);
        if (req.disciplinaId() == null) throw erro(HttpStatus.BAD_REQUEST, "Escolha a disciplina de destino");
        Disciplina destino = carregarDisciplinaAtiva(req.disciplinaId(), professorId);
        if (req.abreEm() != null && req.fechaEm() != null && !req.fechaEm().isAfter(req.abreEm())) {
            throw erro(HttpStatus.BAD_REQUEST, "O fechamento precisa ser depois da abertura");
        }
        String titulo = req.titulo() == null || req.titulo().isBlank() ? origem.getTitulo() + " (cópia)" : req.titulo().trim();

        Lista copia = new Lista();
        copia.setTitulo(titulo);
        copia.setProfessor(origem.getProfessor());
        copia.setDisciplina(destino);
        copia.setStatus("RASCUNHO"); // nunca publicada direto
        copia.setGabaritoLiberado(false);
        copia.setEstudantes(new ArrayList<>());
        copia.setAbreEm(req.abreEm());
        copia.setFechaEm(req.fechaEm());
        copia.setMaxTentativas(origem.getMaxTentativas());
        copia.setRegraNota(origem.getRegraNota());
        copia.setTempoLimiteMinutos(origem.getTempoLimiteMinutos());
        copia.setEmbaralharQuestoes(origem.isEmbaralharQuestoes());
        copia.setEmbaralharAlternativas(origem.isEmbaralharAlternativas());
        Lista salva = listaRepository.save(copia);

        for (Questao q : questaoRepository.findByListaId(listaId)) {
            cloneService.clonar(q, salva);
        }
        return ListaResponseDTO.de(salva);
    }

    // ---------------------------------------------------------------- FE-203

    @Transactional
    public ReabrirResultado reabrir(UUID listaId, UUID professorId, ReabrirRequest req) {
        Lista l = carregarListaParaEditar(listaId, professorId);
        if (req.fechaEm() == null || !req.fechaEm().isAfter(Instant.now())) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe um novo prazo no futuro");
        }
        l.setFechaEm(req.fechaEm());
        if (l.isRascunho()) throw erro(HttpStatus.BAD_REQUEST, "Publique a lista antes de reabrir");
        // Quem já finalizou só consegue refazer se houver tentativa sobrando
        if (req.permitirNovaTentativa() == null || req.permitirNovaTentativa()) {
            l.setMaxTentativas(NotaListaService.maxTentativas(l) + 1);
        }
        listaRepository.save(l);

        Set<UUID> finalizaram = notaRepository.findByListaId(listaId).stream()
                .filter(ListaEstudanteNota::isFinalizada)
                .map(n -> n.getEstudante().getId())
                .collect(Collectors.toSet());
        String link = "/listas/" + listaId + "/questoes";
        int n = 0;
        for (Estudante e : l.getEstudantes() == null ? List.<Estudante>of() : l.getEstudantes()) {
            boolean refaz = finalizaram.contains(e.getId());
            notificacaoService.criar(e.getId(), TipoNotificacao.PRAZO, "Lista reaberta",
                    l.getTitulo() + (refaz ? ": você pode refazer até " : ": novo prazo até ")
                            + java.time.format.DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm")
                            .withZone(java.time.ZoneId.of("America/Sao_Paulo")).format(req.fechaEm()) + ".", link);
            n++;
        }
        return new ReabrirResultado(ListaResponseDTO.de(l), n);
    }

    // ---------------------------------------------------------------- reforço (FE-203/204)

    @Transactional(readOnly = true)
    public List<AssuntoFraco> assuntosFracos(UUID disciplinaId, UUID professorId, int limite) {
        carregarDisciplina(disciplinaId, professorId);
        Map<String, long[]> por = new LinkedHashMap<>(); // assunto -> [acertos, total]
        Map<String, String> rotulo = new HashMap<>();
        Map<String, Set<Integer>> questoes = new HashMap<>();
        for (RespostaEstudantes r : respostaRepository.findDetalhadasByDisciplinaId(disciplinaId)) {
            String assunto = r.getQuestao().getAssunto();
            if (assunto == null || assunto.isBlank() || r.getResposta() == null) continue; // aberta pendente fica de fora
            String k = norm(assunto);
            rotulo.putIfAbsent(k, assunto.trim());
            long[] v = por.computeIfAbsent(k, x -> new long[2]);
            if (Boolean.TRUE.equals(r.getResposta())) v[0]++;
            v[1]++;
            questoes.computeIfAbsent(k, x -> new HashSet<>()).add(r.getQuestao().getId());
        }
        return por.entrySet().stream()
                .map(e -> new AssuntoFraco(rotulo.get(e.getKey()), e.getValue()[0] * 100.0 / e.getValue()[1],
                        e.getValue()[1], questoes.get(e.getKey()).size()))
                .sorted(Comparator.comparingDouble(AssuntoFraco::taxaAcerto))
                .limit(Math.max(1, limite))
                .collect(Collectors.toList());
    }

    @Transactional
    public ReforcoResultado criarReforco(UUID professorId, ReforcoRequest req) {
        if (req.disciplinaId() == null) throw erro(HttpStatus.BAD_REQUEST, "Informe a disciplina");
        Disciplina d = carregarDisciplinaAtiva(req.disciplinaId(), professorId);
        if (req.assuntos() == null || req.assuntos().isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "Escolha ao menos um assunto");
        }
        if (req.titulo() == null || req.titulo().isBlank()) throw erro(HttpStatus.BAD_REQUEST, "Informe o título da lista");
        int porAssunto = Math.min(Math.max(req.quantidadePorAssunto() == null ? 3 : req.quantidadePorAssunto(), 1), 10);

        // Candidatas: questões do próprio professor da disciplina, por assunto, sem repetir enunciado
        UUID donoId = d.getProfessor().getId();
        Set<String> assuntos = req.assuntos().stream().map(ListaGestaoService::norm).collect(Collectors.toSet());
        Map<String, List<Questao>> escolhidas = new LinkedHashMap<>();
        Set<String> vistos = new HashSet<>();
        questaoRepository.findAllComLista().stream()
                .filter(q -> q.getLista().getProfessor() != null && donoId.equals(q.getLista().getProfessor().getId()))
                .filter(q -> assuntos.contains(norm(q.getAssunto())))
                .sorted(Comparator.comparing(Questao::getId).reversed())
                .forEach(q -> {
                    List<Questao> lista = escolhidas.computeIfAbsent(norm(q.getAssunto()), k -> new ArrayList<>());
                    if (lista.size() < porAssunto && vistos.add(norm(q.getEnunciado()))) lista.add(q);
                });
        List<Questao> escolhidasTodas = escolhidas.values().stream().flatMap(List::stream).collect(Collectors.toList());
        if (escolhidasTodas.isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "Não há questões no banco com esses assuntos");
        }

        List<Estudante> alunos;
        if (req.estudanteIds() == null || req.estudanteIds().isEmpty()) {
            alunos = new ArrayList<>(d.getEstudantes());
        } else {
            Set<UUID> daDisciplina = d.getEstudantes().stream().map(Estudante::getId).collect(Collectors.toSet());
            alunos = estudanteRepository.findAllById(req.estudanteIds()).stream()
                    .filter(e -> daDisciplina.contains(e.getId()))
                    .collect(Collectors.toList());
            if (alunos.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Nenhum dos alunos escolhidos faz parte da disciplina");
        }

        boolean publicar = Boolean.TRUE.equals(req.publicar());
        Lista lista = new Lista();
        lista.setTitulo(req.titulo().trim());
        lista.setProfessor(d.getProfessor());
        lista.setDisciplina(d);
        lista.setStatus(publicar ? "PUBLICADA" : "RASCUNHO");
        lista.setEstudantes(publicar ? new ArrayList<>(alunos) : new ArrayList<>());
        lista.setFechaEm(req.fechaEm());
        Lista salva = listaRepository.save(lista);
        for (Questao q : escolhidasTodas) cloneService.clonar(q, salva);
        if (publicar) notificarListaNova(salva, alunos);
        return new ReforcoResultado(ListaResponseDTO.de(salva), escolhidasTodas.size(), alunos.size());
    }

    private void notificarListaNova(Lista lista, Collection<Estudante> alunos) {
        String nomeDisciplina = lista.getDisciplina() != null && lista.getDisciplina().getNome() != null
                ? lista.getDisciplina().getNome() + ": " : "";
        String msg = nomeDisciplina + lista.getTitulo() + " está disponível.";
        String link = "/listas/" + lista.getId() + "/questoes";
        for (Estudante e : alunos) {
            notificacaoService.criarSeNaoExistir(e.getId(), TipoNotificacao.LISTA_NOVA, "Nova lista publicada", msg, link);
        }
    }
}
