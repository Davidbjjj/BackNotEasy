package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** FE-303 (dashboard executivo) e FE-305 (relatórios comparativos): métricas por período, disciplina, turma e professor. */
@Service
public class RelatoriosInstituicaoService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    public record Metricas(int alunos, int alunosAtivos, Double mediaGeral, int entregas, int noPrazo, Double pctNoPrazo) {}

    public record Kpi(String chave, String titulo, String unidade, Double valor, Double anterior, Double variacaoPct) {}

    public record Periodo(LocalDate inicio, LocalDate fim, LocalDate inicioAnterior, LocalDate fimAnterior) {}

    public record Dashboard(Periodo periodo, UUID disciplinaId, List<Kpi> kpis, List<Opcao> disciplinas) {}

    public record Opcao(UUID id, String nome) {}

    public record LinhaDetalhe(UUID id, String nome, String professor, int alunos, int alunosAtivos, Double mediaGeral,
                               int entregas, Double pctNoPrazo) {}

    public record Detalhe(String kpi, String titulo, Periodo periodo, List<LinhaDetalhe> linhas) {}

    public record Comparativo(String por, Periodo periodo, List<LinhaDetalhe> linhas) {}

    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private ListaRepository listaRepository;
    @Autowired private ListaEstudanteNotaRepository notaRepository;
    @Autowired private AcessoEstudanteRepository acessoRepository;
    @Autowired private TurmaRepository turmaRepository;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    private static Double um(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Período pedido (padrão: últimos 30 dias, hoje incluído) e o período anterior de mesma duração. */
    public static Periodo periodo(LocalDate inicio, LocalDate fim) {
        LocalDate f = fim != null ? fim : LocalDate.now(ZONA);
        LocalDate i = inicio != null ? inicio : f.minusDays(29);
        if (i.isAfter(f)) throw erro(HttpStatus.BAD_REQUEST, "O início do período precisa ser antes do fim");
        if (ChronoUnit.DAYS.between(i, f) > 1830) throw erro(HttpStatus.BAD_REQUEST, "Período máximo de 5 anos");
        long dias = ChronoUnit.DAYS.between(i, f) + 1;
        return new Periodo(i, f, i.minusDays(dias), i.minusDays(1));
    }

    private static Instant ini(LocalDate d) {
        return d.atStartOfDay(ZONA).toInstant();
    }

    private static Instant fimExclusivo(LocalDate d) {
        return d.plusDays(1).atStartOfDay(ZONA).toInstant();
    }

    /** Métricas das disciplinas no intervalo [inicio, fim]; {@code restricao} limita aos alunos informados (turma). */
    @Transactional(readOnly = true)
    public Metricas calcular(Collection<Disciplina> ds, Set<UUID> restricao, LocalDate inicio, LocalDate fim) {
        Instant a = ini(inicio);
        Instant b = fimExclusivo(fim);
        Set<UUID> alunos = new HashSet<>();
        for (Disciplina d : ds) {
            for (Estudante e : d.getEstudantes()) {
                if (restricao == null || restricao.contains(e.getId())) alunos.add(e.getId());
            }
        }
        int ativos = alunos.isEmpty() ? 0 : acessoRepository.ativosNoPeriodo(alunos, a, b).size();
        double soma = 0;
        int comNota = 0, entregas = 0, noPrazo = 0;
        for (Disciplina d : ds) {
            for (Lista l : listaRepository.findByDisciplinaId(d.getId())) {
                if (l.isRascunho()) continue;
                Instant prazo = AlunosRiscoService.prazoDe(l);
                for (ListaEstudanteNota n : notaRepository.findByListaId(l.getId())) {
                    if (!n.isFinalizada() || n.getFinalizadaEm() == null) continue;
                    if (n.getFinalizadaEm().isBefore(a) || !n.getFinalizadaEm().isBefore(b)) continue;
                    if (restricao != null && !restricao.contains(n.getEstudante().getId())) continue;
                    entregas++;
                    if (prazo == null || !n.getFinalizadaEm().isAfter(prazo)) noPrazo++;
                    if (n.getNota() != null) {
                        soma += n.getNota().doubleValue();
                        comNota++;
                    }
                }
            }
        }
        return new Metricas(alunos.size(), ativos, comNota == 0 ? null : um(soma / comNota), entregas, noPrazo,
                entregas == 0 ? null : um(noPrazo * 100.0 / entregas));
    }

    private static Double variacao(Double atual, Double anterior) {
        if (atual == null || anterior == null || anterior == 0) return null;
        return um((atual - anterior) * 100.0 / anterior);
    }

    private List<Disciplina> disciplinasDe(UUID instituicaoId, UUID disciplinaId) {
        List<Disciplina> todas = disciplinaRepository.findByInstituicao_Id(instituicaoId);
        if (disciplinaId == null) return todas;
        List<Disciplina> uma = todas.stream().filter(d -> d.getId().equals(disciplinaId)).collect(Collectors.toList());
        if (uma.isEmpty()) throw erro(HttpStatus.NOT_FOUND, "Disciplina não encontrada nesta instituição");
        return uma;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID instituicaoId, LocalDate inicio, LocalDate fim, UUID disciplinaId) {
        Periodo p = periodo(inicio, fim);
        List<Disciplina> ds = disciplinasDe(instituicaoId, disciplinaId);
        Metricas atual = calcular(ds, null, p.inicio(), p.fim());
        Metricas ant = calcular(ds, null, p.inicioAnterior(), p.fimAnterior());
        List<Kpi> kpis = List.of(
                new Kpi("ALUNOS_ATIVOS", "Alunos ativos", "alunos", (double) atual.alunosAtivos(), (double) ant.alunosAtivos(),
                        variacao((double) atual.alunosAtivos(), (double) ant.alunosAtivos())),
                new Kpi("MEDIA_GERAL", "Média geral", "nota", atual.mediaGeral(), ant.mediaGeral(),
                        variacao(atual.mediaGeral(), ant.mediaGeral())),
                new Kpi("ENTREGAS_NO_PRAZO", "Entregas no prazo", "%", atual.pctNoPrazo(), ant.pctNoPrazo(),
                        variacao(atual.pctNoPrazo(), ant.pctNoPrazo())));
        List<Opcao> opcoes = disciplinaRepository.findByInstituicao_Id(instituicaoId).stream()
                .map(d -> new Opcao(d.getId(), d.getNome())).collect(Collectors.toList());
        return new Dashboard(p, disciplinaId, kpis, opcoes);
    }

    private LinhaDetalhe linha(UUID id, String nome, String professor, Collection<Disciplina> ds, Set<UUID> restricao,
                               LocalDate inicio, LocalDate fim) {
        Metricas m = calcular(ds, restricao, inicio, fim);
        return new LinhaDetalhe(id, nome, professor, m.alunos(), m.alunosAtivos(), m.mediaGeral(), m.entregas(), m.pctNoPrazo());
    }

    /** Relatório detalhado de um KPI: uma linha por disciplina, ordenada pelo indicador. */
    @Transactional(readOnly = true)
    public Detalhe detalhe(UUID instituicaoId, String kpi, LocalDate inicio, LocalDate fim, UUID disciplinaId) {
        Periodo p = periodo(inicio, fim);
        String k = kpi == null ? "" : kpi.toUpperCase();
        if (!List.of("ALUNOS_ATIVOS", "MEDIA_GERAL", "ENTREGAS_NO_PRAZO").contains(k)) {
            throw erro(HttpStatus.BAD_REQUEST, "KPI inválido");
        }
        List<LinhaDetalhe> linhas = new ArrayList<>();
        for (Disciplina d : disciplinasDe(instituicaoId, disciplinaId)) {
            linhas.add(linha(d.getId(), d.getNome(), d.getProfessor() == null ? null : d.getProfessor().getNome(),
                    List.of(d), null, p.inicio(), p.fim()));
        }
        Comparator<LinhaDetalhe> ordem = switch (k) {
            case "ALUNOS_ATIVOS" -> Comparator.comparingInt(LinhaDetalhe::alunosAtivos).reversed();
            case "MEDIA_GERAL" -> Comparator.comparing((LinhaDetalhe l) -> l.mediaGeral() == null ? -1.0 : l.mediaGeral()).reversed();
            default -> Comparator.comparing((LinhaDetalhe l) -> l.pctNoPrazo() == null ? -1.0 : l.pctNoPrazo()).reversed();
        };
        linhas.sort(ordem);
        String titulo = switch (k) {
            case "ALUNOS_ATIVOS" -> "Alunos ativos por disciplina";
            case "MEDIA_GERAL" -> "Média geral por disciplina";
            default -> "Entregas no prazo por disciplina";
        };
        return new Detalhe(k, titulo, p, linhas);
    }

    /** FE-305: compara turmas, disciplinas ou professores. {@code ids} vazio = todos. */
    @Transactional(readOnly = true)
    public Comparativo comparar(UUID instituicaoId, String por, Collection<UUID> ids, LocalDate inicio, LocalDate fim) {
        Periodo p = periodo(inicio, fim);
        String tipo = por == null ? "" : por.toUpperCase();
        Set<UUID> filtro = ids == null || ids.isEmpty() ? null : new HashSet<>(ids);
        List<LinhaDetalhe> linhas = new ArrayList<>();
        switch (tipo) {
            case "DISCIPLINA" -> {
                for (Disciplina d : disciplinaRepository.findByInstituicao_Id(instituicaoId)) {
                    if (filtro != null && !filtro.contains(d.getId())) continue;
                    linhas.add(linha(d.getId(), d.getNome(), d.getProfessor() == null ? null : d.getProfessor().getNome(),
                            List.of(d), null, p.inicio(), p.fim()));
                }
            }
            case "TURMA" -> {
                for (Turma t : turmaRepository.findByInstituicaoIdOrderByNomeAsc(instituicaoId)) {
                    if (filtro != null && !filtro.contains(t.getId())) continue;
                    Set<UUID> alunos = t.getEstudantes().stream().map(Estudante::getId).collect(Collectors.toSet());
                    linhas.add(linha(t.getId(), t.getNome() + " (" + t.getPeriodo().getNome() + ")", null, t.getDisciplinas(),
                            alunos, p.inicio(), p.fim()));
                }
            }
            case "PROFESSOR" -> {
                Map<UUID, List<Disciplina>> porProf = new LinkedHashMap<>();
                for (Disciplina d : disciplinaRepository.findByInstituicao_Id(instituicaoId)) {
                    if (d.getProfessor() == null) continue;
                    if (filtro != null && !filtro.contains(d.getProfessor().getId())) continue;
                    porProf.computeIfAbsent(d.getProfessor().getId(), x -> new ArrayList<>()).add(d);
                }
                for (List<Disciplina> ds : porProf.values()) {
                    Professor prof = ds.get(0).getProfessor();
                    linhas.add(linha(prof.getId(), prof.getNome(), prof.getNome(), ds, null, p.inicio(), p.fim()));
                }
            }
            default -> throw erro(HttpStatus.BAD_REQUEST, "Escolha comparar por TURMA, DISCIPLINA ou PROFESSOR");
        }
        return new Comparativo(tipo, p, linhas);
    }
}
