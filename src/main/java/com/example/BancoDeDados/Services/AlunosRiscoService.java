package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** FE-204 (alunos em risco) e FE-207 (perfil do aluno). */
@Service
public class AlunosRiscoService {

    /** Critérios de risco. Valem para o painel inteiro e são devolvidos na resposta para ficarem visíveis na tela. */
    public static final double MEDIA_MINIMA = 6.0;
    public static final int DIAS_SEM_ACESSO = 14;
    public static final int LISTAS_ATRASADAS = 1;

    public record Criterios(double mediaMinima, int diasSemAcesso, int listasAtrasadas, String regra) {}

    public record Opcao(UUID id, String nome) {}

    public record AlunoRisco(UUID estudanteId, String nome, String email, Double media, Instant ultimoAcesso,
                             Integer diasSemAcesso, int listasAtrasadas, int listasPendentes,
                             List<String> motivos, String nivel) {}

    public record Painel(Criterios criterios, List<AlunoRisco> items, int totalAlunos, List<Opcao> disciplinas) {}

    public record HistoricoLista(UUID listaId, String titulo, UUID disciplinaId, String disciplinaNome, Double nota,
                                 String status, Instant prazo, Instant entregueEm, int tentativas) {}

    public record Resumo(Double media, int entregues, int atrasadas, int naoFeitas) {}

    public record NotaPonto(UUID listaId, String titulo, UUID disciplinaId, Instant data, double nota, double notaMaxima) {}

    public record AssuntoDesempenho(String id, String nome, UUID disciplinaId, int acertos, int total) {}

    /** Mesmo formato do GET /estudante/{id}/evolucao (FE-105), para reaproveitar os gráficos. */
    public record Evolucao(List<Opcao> disciplinas, List<NotaPonto> notas, List<AssuntoDesempenho> assuntos) {}

    public record Perfil(UUID id, String nome, String email, String instituicao, List<Opcao> disciplinas,
                         Resumo resumo, List<HistoricoLista> historico, Evolucao evolucao, List<Instant> acessos) {}

    @Autowired
    private DisciplinaRepository disciplinaRepository;
    @Autowired
    private ListaRepository listaRepository;
    @Autowired
    private ListaEstudanteNotaRepository notaRepository;
    @Autowired
    private AcessoEstudanteRepository acessoRepository;
    @Autowired
    private EstudanteRepositores estudanteRepository;
    @Autowired
    private RespostaEstudantesRepository respostaRepository;
    @Autowired
    private QuestaoRepositores questaoRepository;
    @Autowired
    private NotificacaoService notificacaoService;

    private List<Disciplina> disciplinasDoEscopo(UUID professorId, UUID instituicaoId) {
        if (professorId != null) return disciplinaRepository.findByProfessorId(professorId);
        if (instituicaoId != null) return disciplinaRepository.findByInstituicao_Id(instituicaoId);
        return disciplinaRepository.findAll();
    }

    static Instant prazoDe(Lista l) {
        if (l.getFechaEm() != null) return l.getFechaEm();
        if (l.getEventos() == null) return null;
        return l.getEventos().stream()
                .map(ListaEvento::getEvento)
                .filter(e -> e != null && e.getData() != null)
                .map(e -> e.getData().atZone(ZoneId.systemDefault()).toInstant())
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean temAluno(Lista l, UUID estudanteId) {
        return l.getEstudantes() != null && l.getEstudantes().stream().anyMatch(e -> estudanteId.equals(e.getId()));
    }

    private static Double um(double v) {
        return BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------------------------------------------------------- FE-204

    @Transactional(readOnly = true)
    public Painel painel(UUID professorId, UUID instituicaoId, UUID disciplinaId, boolean todos,
                         Double mediaMinima, Integer diasSemAcesso, Integer listasAtrasadas) {
        double minMedia = mediaMinima == null ? MEDIA_MINIMA : mediaMinima;
        int minDias = diasSemAcesso == null ? DIAS_SEM_ACESSO : diasSemAcesso;
        int minAtrasadas = listasAtrasadas == null ? LISTAS_ATRASADAS : Math.max(1, listasAtrasadas);

        List<Disciplina> escopo = disciplinasDoEscopo(professorId, instituicaoId);
        List<Opcao> opcoes = escopo.stream().map(d -> new Opcao(d.getId(), d.getNome())).collect(Collectors.toList());
        if (disciplinaId != null) {
            escopo = escopo.stream().filter(d -> d.getId().equals(disciplinaId)).collect(Collectors.toList());
            if (escopo.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Disciplina fora do seu escopo");
        }

        return montar(escopo, null, todos, opcoes, minMedia, minDias, minAtrasadas);
    }

    /** FE-308: risco e evasão de um conjunto de disciplinas, restrito a alguns alunos (ex.: uma turma). */
    @Transactional(readOnly = true)
    public Painel painelDe(List<Disciplina> escopo, Set<UUID> restricao) {
        return montar(escopo, restricao, true, List.of(), MEDIA_MINIMA, DIAS_SEM_ACESSO, LISTAS_ATRASADAS);
    }

    private Painel montar(List<Disciplina> escopo, Set<UUID> restricao, boolean todos, List<Opcao> opcoes,
                          double minMedia, int minDias, int minAtrasadas) {
        Map<UUID, Estudante> alunos = new LinkedHashMap<>();
        List<Lista> listas = new ArrayList<>();
        for (Disciplina d : escopo) {
            d.getEstudantes().stream().filter(e -> restricao == null || restricao.contains(e.getId()))
                    .forEach(e -> alunos.putIfAbsent(e.getId(), e));
            listaRepository.findByDisciplinaId(d.getId()).stream().filter(l -> !l.isRascunho()).forEach(listas::add);
        }
        Set<UUID> listaIds = listas.stream().map(Lista::getId).collect(Collectors.toSet());

        Map<UUID, Instant> ultimos = new HashMap<>();
        if (!alunos.isEmpty()) {
            acessoRepository.ultimosAcessos(alunos.keySet()).forEach(u -> ultimos.put(u.getEstudanteId(), u.getUltimo()));
        }

        Instant agora = Instant.now();
        List<AlunoRisco> out = new ArrayList<>();
        for (Estudante e : alunos.values()) {
            Map<UUID, ListaEstudanteNota> notas = notaRepository.findByEstudanteId(e.getId()).stream()
                    .filter(n -> listaIds.contains(n.getLista().getId()))
                    .collect(Collectors.toMap(n -> n.getLista().getId(), n -> n, (a, b) -> a));
            List<Double> finalizadas = notas.values().stream()
                    .filter(n -> n.isFinalizada() && n.getNota() != null)
                    .map(n -> n.getNota().doubleValue()).collect(Collectors.toList());
            Double media = finalizadas.isEmpty() ? null
                    : um(finalizadas.stream().mapToDouble(Double::doubleValue).average().orElse(0));

            int atrasadas = 0;
            int pendentes = 0;
            for (Lista l : listas) {
                if (!temAluno(l, e.getId())) continue;
                ListaEstudanteNota n = notas.get(l.getId());
                if (n != null && n.isFinalizada()) continue;
                pendentes++;
                Instant prazo = prazoDe(l);
                if (prazo != null && prazo.isBefore(agora)) atrasadas++;
            }

            Instant ultimo = ultimos.get(e.getId());
            Integer dias = ultimo == null ? null : (int) Duration.between(ultimo, agora).toDays();

            List<String> motivos = new ArrayList<>();
            if (media != null && media < minMedia) motivos.add("MEDIA_BAIXA");
            if (ultimo == null || dias >= minDias) motivos.add("SEM_ACESSO");
            if (atrasadas >= minAtrasadas) motivos.add("LISTAS_ATRASADAS");
            if (motivos.isEmpty() && !todos) continue;
            out.add(new AlunoRisco(e.getId(), e.getNome(), e.getEmail(), media, ultimo, dias, atrasadas, pendentes,
                    motivos, motivos.size() >= 2 ? "ALTO" : motivos.size() == 1 ? "MEDIO" : "BAIXO"));
        }
        out.sort(Comparator
                .comparing((AlunoRisco a) -> a.motivos().size()).reversed()
                .thenComparing(a -> a.media() == null ? 11.0 : a.media())
                .thenComparing(AlunoRisco::nome, String.CASE_INSENSITIVE_ORDER));

        String regra = "Em risco quando: média abaixo de " + minMedia + ", sem acesso há " + minDias
                + " dias ou mais (ou nunca acessou), ou " + minAtrasadas + " lista(s) atrasada(s) ou mais.";
        return new Painel(new Criterios(minMedia, minDias, minAtrasadas, regra), out, alunos.size(), opcoes);
    }

    // ---------------------------------------------------------------- FE-207

    private static String slug(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim()
                .replaceAll("[^a-z0-9]+", "-");
    }

    @Transactional(readOnly = true)
    public Perfil perfil(UUID estudanteId, UUID professorId, UUID instituicaoId) {
        Estudante e = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aluno não encontrado"));
        List<Disciplina> escopo = disciplinasDoEscopo(professorId, instituicaoId).stream()
                .filter(d -> d.getEstudantes().stream().anyMatch(x -> x.getId().equals(estudanteId)))
                .collect(Collectors.toList());
        if (escopo.isEmpty() && (professorId != null || instituicaoId != null)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Este aluno não está em nenhuma das suas disciplinas");
        }

        List<Lista> listas = new ArrayList<>();
        for (Disciplina d : escopo) {
            listaRepository.findByDisciplinaId(d.getId()).stream()
                    .filter(l -> !l.isRascunho() && temAluno(l, estudanteId)).forEach(listas::add);
        }
        Map<UUID, ListaEstudanteNota> notas = notaRepository.findByEstudanteId(estudanteId).stream()
                .collect(Collectors.toMap(n -> n.getLista().getId(), n -> n, (a, b) -> a));
        Map<UUID, Set<Integer>> respondidasPorLista = new HashMap<>();
        Map<Integer, RespostaEstudantes> respostaPorQuestao = new HashMap<>();
        for (RespostaEstudantes r : respostaRepository.findDetalhadasByEstudanteId(estudanteId)) {
            respostaPorQuestao.put(r.getQuestao().getId(), r);
            respondidasPorLista.computeIfAbsent(r.getQuestao().getLista().getId(), k -> new HashSet<>()).add(r.getQuestao().getId());
        }

        Instant agora = Instant.now();
        List<HistoricoLista> historico = new ArrayList<>();
        int entregues = 0;
        int atrasadas = 0;
        int naoFeitas = 0;
        List<Double> entreguesNotas = new ArrayList<>();
        List<NotaPonto> pontos = new ArrayList<>();
        Map<String, int[]> assuntos = new LinkedHashMap<>();
        Map<String, String> nomeAssunto = new HashMap<>();
        Map<String, UUID> discAssunto = new HashMap<>();

        for (Lista l : listas) {
            ListaEstudanteNota n = notas.get(l.getId());
            Instant prazo = prazoDe(l);
            Disciplina d = l.getDisciplina();
            String status;
            if (n != null && n.isFinalizada()) {
                status = "ENTREGUE";
                entregues++;
                if (n.getNota() != null) {
                    entreguesNotas.add(n.getNota().doubleValue());
                    pontos.add(new NotaPonto(l.getId(), l.getTitulo(), d == null ? null : d.getId(),
                            n.getFinalizadaEm() == null ? agora : n.getFinalizadaEm(), n.getNota().doubleValue(), 10));
                }
                // desempenho por assunto: questões objetivas das listas entregues (em branco conta como errada)
                for (Questao q : questaoRepository.findByListaId(l.getId())) {
                    if (q.isAberta() || q.getAssunto() == null || q.getAssunto().isBlank()) continue;
                    String id = slug(q.getAssunto());
                    nomeAssunto.putIfAbsent(id, q.getAssunto().trim());
                    discAssunto.putIfAbsent(id, d == null ? null : d.getId());
                    int[] v = assuntos.computeIfAbsent(id, k -> new int[2]);
                    v[1]++;
                    RespostaEstudantes r = respostaPorQuestao.get(q.getId());
                    if (r != null && r.isCorreta()) v[0]++;
                }
            } else if (prazo != null && prazo.isBefore(agora)) {
                status = "ATRASADA";
                atrasadas++;
            } else {
                status = "NAO_FEITA";
                naoFeitas++;
            }
            historico.add(new HistoricoLista(l.getId(), l.getTitulo(), d == null ? null : d.getId(),
                    d == null ? null : d.getNome(), n == null || n.getNota() == null ? null : n.getNota().doubleValue(),
                    status, prazo, n == null ? null : n.getFinalizadaEm(), n == null || n.getTentativas() == null ? 0 : n.getTentativas()));
        }
        historico.sort(Comparator.comparing((HistoricoLista h) -> h.prazo() == null ? Instant.MAX : h.prazo()).reversed());
        pontos.sort(Comparator.comparing(NotaPonto::data));

        List<AssuntoDesempenho> assuntosOut = assuntos.entrySet().stream()
                .map(en -> new AssuntoDesempenho(en.getKey(), nomeAssunto.get(en.getKey()), discAssunto.get(en.getKey()),
                        en.getValue()[0], en.getValue()[1]))
                .collect(Collectors.toList());
        List<Opcao> disciplinasOut = escopo.stream().map(d -> new Opcao(d.getId(), d.getNome())).collect(Collectors.toList());
        Double media = entreguesNotas.isEmpty() ? null
                : um(entreguesNotas.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        List<Instant> acessos = acessoRepository.findByEstudanteIdOrderByAcessoEmDesc(estudanteId, PageRequest.of(0, 10))
                .stream().map(AcessoEstudante::getAcessoEm).collect(Collectors.toList());

        return new Perfil(e.getId(), e.getNome(), e.getEmail(), e.getInstituicao(), disciplinasOut,
                new Resumo(media, entregues, atrasadas, naoFeitas), historico,
                new Evolucao(disciplinasOut, pontos, assuntosOut), acessos);
    }

    // ---------------------------------------------------------------- avisar aluno (FE-203/204)

    /** Envia um aviso (notificação GERAL) a alunos das disciplinas do chamador. Retorna quantos receberam. */
    @Transactional
    public int avisar(UUID professorId, UUID instituicaoId, List<UUID> estudanteIds, String titulo, String mensagem) {
        if (estudanteIds == null || estudanteIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha ao menos um aluno");
        }
        if (estudanteIds.size() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha no máximo 500 alunos por vez");
        }
        if (mensagem == null || mensagem.isBlank() || mensagem.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mensagem é obrigatória e pode ter até 500 caracteres");
        }
        Set<UUID> permitidos = new HashSet<>();
        for (Disciplina d : disciplinasDoEscopo(professorId, instituicaoId)) {
            d.getEstudantes().forEach(e -> permitidos.add(e.getId()));
        }
        Set<UUID> alvo = new LinkedHashSet<>(estudanteIds);
        if (!permitidos.containsAll(alvo)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Há alunos que não fazem parte das suas disciplinas");
        }
        String t = titulo == null || titulo.isBlank() ? "Aviso do professor" : titulo.trim();
        for (UUID id : alvo) {
            notificacaoService.criar(id, TipoNotificacao.GERAL, t, mensagem.trim(), null);
        }
        return alvo.size();
    }
}
