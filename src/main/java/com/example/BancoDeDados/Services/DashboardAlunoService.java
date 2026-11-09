package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Exceptions.ResourceNotFoundException;
import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.ListaEstudanteNota;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.ListaEstudanteNotaRepository;
import com.example.BancoDeDados.Repositores.NotaEventoRepository;
import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;
import com.example.BancoDeDados.Repositores.projections.AtividadeListaProjection;
import com.example.BancoDeDados.ResponseDTO.DashboardAlunoDTO;
import com.example.BancoDeDados.ResponseDTO.DashboardAlunoDTO.ListaEmAndamento;
import com.example.BancoDeDados.ResponseDTO.DashboardAlunoDTO.ProximaEntrega;
import com.example.BancoDeDados.ResponseDTO.DashboardAlunoDTO.Sequencia;
import com.example.BancoDeDados.ResponseDTO.DashboardAlunoDTO.UltimaNota;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DashboardAlunoService {

    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Sao_Paulo");
    public static final int LIMITE_EM_ANDAMENTO = 10;
    public static final int LIMITE_PROXIMAS_ENTREGAS = 5;
    public static final int LIMITE_ULTIMAS_NOTAS = 5;
    /** Nota da lista (NotaListaService) vai de 0 a 10. */
    public static final double NOTA_MAXIMA_LISTA = 10.0;

    private final EstudanteRepositores estudanteRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final RespostaEstudantesRepository respostaRepository;
    private final ListaEstudanteNotaRepository listaEstudanteNotaRepository;
    private final NotaEventoRepository notaEventoRepository;

    public DashboardAlunoService(EstudanteRepositores estudanteRepository,
                                 DisciplinaRepository disciplinaRepository,
                                 RespostaEstudantesRepository respostaRepository,
                                 ListaEstudanteNotaRepository listaEstudanteNotaRepository,
                                 NotaEventoRepository notaEventoRepository) {
        this.estudanteRepository = estudanteRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.respostaRepository = respostaRepository;
        this.listaEstudanteNotaRepository = listaEstudanteNotaRepository;
        this.notaEventoRepository = notaEventoRepository;
    }

    /**
     * @param fusoSolicitado valor do header X-Timezone (IANA); null/vazio = usa o fuso do aluno
     * @throws IllegalArgumentException se fusoSolicitado não for um fuso IANA válido
     */
    @Transactional(readOnly = true)
    public DashboardAlunoDTO montar(UUID estudanteId, String fusoSolicitado) {
        return montar(estudanteId, fusoSolicitado, Instant.now());
    }

    @Transactional(readOnly = true)
    public DashboardAlunoDTO montar(UUID estudanteId, String fusoSolicitado, Instant agora) {
        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudante não encontrado"));

        ZoneId fuso = resolverFuso(fusoSolicitado, estudante.getFusoHorario());

        return new DashboardAlunoDTO(
                estudante.getId(),
                estudante.getNome(),
                fuso.getId(),
                contarDisciplinas(estudante),
                montarSequencia(estudanteId, fuso, agora),
                montarEmAndamento(estudanteId, fuso),
                montarProximasEntregas(estudanteId, fuso, agora),
                montarUltimasNotas(estudanteId, fuso)
        );
    }

    /** Header válido > fuso salvo do aluno (se válido) > America/Sao_Paulo. */
    static ZoneId resolverFuso(String fusoSolicitado, String fusoDoAluno) {
        if (fusoSolicitado != null && !fusoSolicitado.isBlank()) {
            try {
                return ZoneId.of(fusoSolicitado.trim());
            } catch (DateTimeException e) {
                throw new IllegalArgumentException("Fuso horário inválido: " + fusoSolicitado);
            }
        }
        if (fusoDoAluno != null && !fusoDoAluno.isBlank()) {
            try {
                return ZoneId.of(fusoDoAluno.trim());
            } catch (DateTimeException ignored) {
                // valor antigo/corrompido: cai no padrão
            }
        }
        return FUSO_PADRAO;
    }

    private int contarDisciplinas(Estudante estudante) {
        // Existem duas relações aluno<->disciplina no modelo; une as duas sem duplicar.
        Set<UUID> ids = new HashSet<>();
        if (estudante.getDisciplina() != null) {
            estudante.getDisciplina().stream().map(Disciplina::getId).forEach(ids::add);
        }
        disciplinaRepository.findByEstudantesId(estudante.getId()).stream().map(Disciplina::getId).forEach(ids::add);
        return ids.size();
    }

    private Sequencia montarSequencia(UUID estudanteId, ZoneId fuso, Instant agora) {
        List<Instant> instantes = new java.util.ArrayList<>();
        for (Object[] linha : respostaRepository.findInstantesAtividade(estudanteId)) {
            if (linha[0] != null) instantes.add((Instant) linha[0]);
            if (linha[1] != null) instantes.add((Instant) linha[1]);
        }
        Instant ultima = instantes.stream().max(Comparator.naturalOrder()).orElse(null);
        return new Sequencia(sequenciaAtual(instantes, fuso, agora), formatar(ultima, fuso));
    }

    /**
     * Dias consecutivos com atividade, contando para trás a partir de hoje.
     * Se hoje ainda não teve atividade, a sequência continua viva a partir de ontem.
     */
    static int sequenciaAtual(Collection<Instant> instantes, ZoneId fuso, Instant agora) {
        Set<LocalDate> dias = new HashSet<>();
        for (Instant i : instantes) {
            dias.add(i.atZone(fuso).toLocalDate());
        }
        LocalDate hoje = agora.atZone(fuso).toLocalDate();
        LocalDate cursor = dias.contains(hoje) ? hoje : hoje.minusDays(1);
        int total = 0;
        while (dias.contains(cursor)) {
            total++;
            cursor = cursor.minusDays(1);
        }
        return total;
    }

    private List<ListaEmAndamento> montarEmAndamento(UUID estudanteId, ZoneId fuso) {
        List<ListaEstudanteNota> parciais = listaEstudanteNotaRepository.findEmAndamentoByEstudanteId(estudanteId);
        if (parciais.isEmpty()) {
            return List.of();
        }
        Map<UUID, Instant> ultimaPorLista = new HashMap<>();
        for (AtividadeListaProjection p : respostaRepository.findUltimaAtividadePorLista(estudanteId)) {
            ultimaPorLista.put(p.getListaId(), p.getUltimaAtividade());
        }

        // Mais recente primeiro; sem timestamp vai para o fim.
        Comparator<ListaEstudanteNota> maisRecente = Comparator.comparing(
                (ListaEstudanteNota n) -> ultimaPorLista.get(n.getLista().getId()),
                Comparator.nullsLast(Comparator.reverseOrder()));

        return parciais.stream()
                .sorted(maisRecente)
                .limit(LIMITE_EM_ANDAMENTO)
                .map(n -> {
                    Disciplina d = n.getLista().getDisciplina();
                    return new ListaEmAndamento(
                            n.getLista().getId(),
                            n.getLista().getTitulo(),
                            d != null ? d.getId() : null,
                            d != null ? d.getNome() : null,
                            n.getQuestoesRespondidas(),
                            n.getTotalQuestoes(),
                            formatar(ultimaPorLista.get(n.getLista().getId()), fuso));
                })
                .toList();
    }

    private List<UltimaNota> montarUltimasNotas(UUID estudanteId, ZoneId fuso) {
        List<ListaEstudanteNota> finalizadas = listaEstudanteNotaRepository.findFinalizadasComNotaByEstudanteId(estudanteId);
        if (finalizadas.isEmpty()) {
            return List.of();
        }
        Map<UUID, Instant> ultimaPorLista = new HashMap<>();
        for (AtividadeListaProjection p : respostaRepository.findUltimaAtividadePorLista(estudanteId)) {
            ultimaPorLista.put(p.getListaId(), p.getUltimaAtividade());
        }
        // Mais recente primeiro; sem timestamp vai para o fim.
        Comparator<ListaEstudanteNota> maisRecente = Comparator.comparing(
                (ListaEstudanteNota n) -> ultimaPorLista.get(n.getLista().getId()),
                Comparator.nullsLast(Comparator.reverseOrder()));

        return finalizadas.stream()
                .sorted(maisRecente)
                .limit(LIMITE_ULTIMAS_NOTAS)
                .map(n -> {
                    Disciplina d = n.getLista().getDisciplina();
                    return new UltimaNota(
                            n.getLista().getId(),
                            n.getLista().getTitulo(),
                            d != null ? d.getId() : null,
                            d != null ? d.getNome() : null,
                            n.getNota().doubleValue(),
                            NOTA_MAXIMA_LISTA,
                            formatar(ultimaPorLista.get(n.getLista().getId()), fuso));
                })
                .toList();
    }

    private List<ProximaEntrega> montarProximasEntregas(UUID estudanteId, ZoneId fuso, Instant agora) {
        // Evento.data é LocalDateTime sem fuso: é lida como hora local no fuso do aluno.
        LocalDateTime agoraLocal = LocalDateTime.ofInstant(agora, fuso);
        return notaEventoRepository
                .findProximasEntregas(estudanteId, NotaEvento.StatusEntrega.PENDENTE, agoraLocal,
                        PageRequest.of(0, LIMITE_PROXIMAS_ENTREGAS))
                .stream()
                .map(ne -> {
                    Disciplina d = ne.getEvento().getDisciplina();
                    return new ProximaEntrega(
                            ne.getEvento().getId(),
                            ne.getEvento().getTitulo(),
                            d != null ? d.getId() : null,
                            d != null ? d.getNome() : null,
                            ne.getEvento().getData().atZone(fuso).toOffsetDateTime()
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                })
                .toList();
    }

    private static String formatar(Instant instante, ZoneId fuso) {
        return instante == null ? null
                : instante.atZone(fuso).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
