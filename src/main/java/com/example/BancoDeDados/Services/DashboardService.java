package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.Model.RespostaEstudantes;
import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.ListaEstudanteNota;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.NotaEventoRepository;
import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.Repositores.ListaEstudanteNotaRepository;
import com.example.BancoDeDados.ResponseDTO.MetricasAlunoDTO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class DashboardService {

    private final ListaRepository listaRepository;
    private final NotaEventoRepository notaEventoRepository;
    private final RespostaEstudantesRepository respostaEstudantesRepository;
    private final EstudanteRepositores estudanteRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final ListaEstudanteNotaRepository listaEstudanteNotaRepository;

    @Autowired
    public DashboardService(
            ListaRepository listaRepository,
            NotaEventoRepository notaEventoRepository,
            RespostaEstudantesRepository respostaEstudantesRepository,
            EstudanteRepositores estudanteRepository,
            DisciplinaRepository disciplinaRepository,
            ListaEstudanteNotaRepository listaEstudanteNotaRepository) {
        this.listaRepository = listaRepository;
        this.notaEventoRepository = notaEventoRepository;
        this.respostaEstudantesRepository = respostaEstudantesRepository;
        this.estudanteRepository = estudanteRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.listaEstudanteNotaRepository = listaEstudanteNotaRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardByListaId(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("tituloLista", lista.getTitulo());
        dashboard.put("professor", lista.getProfessor().getNome());

        // ✅ Buscar todas as respostas da lista de uma vez (otimizado)
        List<RespostaEstudantes> todasRespostas =
            respostaEstudantesRepository.findByListaIdWithJoins(listaId);

        // ✅ Agrupar respostas por questão ID
        Map<Integer, List<RespostaEstudantes>> respostasPorQuestao =
            todasRespostas.stream()
                .collect(Collectors.groupingBy(r -> r.getQuestao().getId()));

        List<Map<String, Object>> questoesData = lista.getQuestoes().stream().map(questao -> {
            Map<String, Object> questaoInfo = new HashMap<>();
            questaoInfo.put("id", questao.getId());
            questaoInfo.put("enunciado", questao.getEnunciado());

            Integer gabarito = questao.getGabarito();
            questaoInfo.put("gabarito", gabarito);

            // ✅ Pegar respostas da questão do mapa (sem N+1 query)
            List<Map<String, Object>> respostasEstudantes =
                respostasPorQuestao.getOrDefault(questao.getId(), Collections.emptyList())
                    .stream()
                    .map(resposta -> {
                        Map<String, Object> respostaInfo = new HashMap<>();
                        respostaInfo.put("estudante", resposta.getEstudante().getNome());
                        respostaInfo.put("respostaDada", resposta.getResposta());
                        return respostaInfo;
                    })
                    .collect(Collectors.toList());

            questaoInfo.put("respostas", respostasEstudantes);
            return questaoInfo;
        }).collect(Collectors.toList());

        dashboard.put("questoes", questoesData);
        return dashboard;
    }

    public Map<String, Object> getDashboardByProfessorId(UUID professorId) {
        List<Lista> listas = listaRepository.findByProfessorId(professorId);

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("listas", listas.stream().map(lista -> {
            Map<String, Object> listaInfo = new HashMap<>();
            listaInfo.put("id", lista.getId());
            listaInfo.put("titulo", lista.getTitulo());
            listaInfo.put("professor", lista.getProfessor().getNome());
            listaInfo.put("questoes", lista.getQuestoes().size());
            listaInfo.put("estudantes", lista.getEstudantes().size());
            return listaInfo;
        }).collect(Collectors.toList()));

        return dashboard;
    }

    public Map<String, Object> getDesempenhoGeralPorPeriodo(LocalDateTime startDate,
                                LocalDateTime endDate,
                                UUID disciplinaId) {
    List<NotaEvento> notas = notaEventoRepository.findByPeriodoAndDisciplina(startDate, endDate, disciplinaId);

    int totalAvaliacoes = notas.size();
    long alunosAvaliados = notas.stream()
        .filter(n -> n.getEstudante() != null)
        .map(n -> n.getEstudante().getId())
        .distinct()
        .count();

    double mediaNotas = notas.stream()
        .map(NotaEvento::getNota)
        .filter(Objects::nonNull)
        .mapToDouble(Double::doubleValue)
        .average()
        .orElse(0.0);

    double mediaPercentual = notas.stream()
        .filter(n -> n.getNota() != null && n.getEvento() != null && n.getEvento().getNotaMaxima() != null && n.getEvento().getNotaMaxima() > 0)
        .mapToDouble(n -> (n.getNota() / n.getEvento().getNotaMaxima()) * 100.0)
        .average()
        .orElse(0.0);

    Map<String, Object> dashboard = new HashMap<>();
    dashboard.put("inicio", startDate);
    dashboard.put("fim", endDate);
    dashboard.put("materiaId", disciplinaId);
    dashboard.put("totalAvaliacoes", totalAvaliacoes);
    dashboard.put("alunosAvaliados", alunosAvaliados);
    dashboard.put("mediaNotas", mediaNotas);
    dashboard.put("mediaPercentual", mediaPercentual);
    return dashboard;
    }
    
    @Transactional(readOnly = true)
    public MetricasAlunoDTO getMetricasAlunoDisciplina(UUID alunoId, UUID disciplinaId) {
        // Buscar estudante e disciplina
        Estudante estudante = estudanteRepository.findById(alunoId)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new RuntimeException("Disciplina não encontrada"));

        MetricasAlunoDTO metricas = new MetricasAlunoDTO();
        metricas.setAlunoId(alunoId);
        metricas.setNomeAluno(estudante.getNome());
        metricas.setDisciplinaId(disciplinaId);
        metricas.setNomeDisciplina(disciplina.getNome());

        // ===== MÉTRICAS DE EVENTOS =====
        List<NotaEvento> notasEventos = notaEventoRepository.findByEstudanteIdAndEventoDisciplinaId(alunoId, disciplinaId);

        metricas.setTotalEventos(notasEventos.size());

        long eventosEntregues = notasEventos.stream()
                .filter(ne -> ne.getStatusEntrega() == NotaEvento.StatusEntrega.ENTREGUE)
                .count();
        metricas.setEventosEntregues((int) eventosEntregues);
        metricas.setEventosPendentes(notasEventos.size() - (int) eventosEntregues);

        // Calcular média de eventos
        double mediaEventos = notasEventos.stream()
                .filter(ne -> ne.getNota() != null)
                .mapToDouble(NotaEvento::getNota)
                .average()
                .orElse(0.0);
        metricas.setMediaEventos(Math.round(mediaEventos * 100.0) / 100.0);

        // Calcular média percentual de eventos
        double mediaPercentualEventos = notasEventos.stream()
                .filter(ne -> ne.getNota() != null && ne.getEvento() != null
                        && ne.getEvento().getNotaMaxima() != null && ne.getEvento().getNotaMaxima() > 0)
                .mapToDouble(ne -> (ne.getNota() / ne.getEvento().getNotaMaxima()) * 100.0)
                .average()
                .orElse(0.0);
        metricas.setMediaPercentualEventos(Math.round(mediaPercentualEventos * 100.0) / 100.0);

        // Detalhes dos eventos
        List<MetricasAlunoDTO.EventoDetalhe> eventosDetalhes = notasEventos.stream()
                .map(ne -> {
                    MetricasAlunoDTO.EventoDetalhe detalhe = new MetricasAlunoDTO.EventoDetalhe();
                    detalhe.setEventoId(ne.getEvento().getId());
                    detalhe.setTitulo(ne.getEvento().getTitulo());
                    detalhe.setNota(ne.getNota());
                    detalhe.setNotaMaxima(ne.getEvento().getNotaMaxima());

                    if (ne.getNota() != null && ne.getEvento().getNotaMaxima() != null && ne.getEvento().getNotaMaxima() > 0) {
                        double percentual = (ne.getNota() / ne.getEvento().getNotaMaxima()) * 100.0;
                        detalhe.setPercentual(Math.round(percentual * 100.0) / 100.0);
                    } else {
                        detalhe.setPercentual(0.0);
                    }

                    detalhe.setStatus(ne.getStatusEntrega() != null ? ne.getStatusEntrega().name() : "PENDENTE");
                    return detalhe;
                })
                .collect(Collectors.toList());
        metricas.setEventos(eventosDetalhes);

        // ===== MÉTRICAS DE LISTAS =====
        List<Lista> listasDaDisciplina = listaRepository.findByDisciplinaId(disciplinaId);

        // Filtrar apenas listas que o aluno está inscrito
        List<Lista> listasDoAluno = listasDaDisciplina.stream()
                .filter(lista -> lista.getEstudantes() != null && lista.getEstudantes().stream()
                        .anyMatch(e -> e.getId().equals(alunoId)))
                .collect(Collectors.toList());

        metricas.setTotalListas(listasDoAluno.size());

        // Buscar notas das listas
        List<ListaEstudanteNota> notasListas = listaEstudanteNotaRepository.findByEstudanteIdAndListaIdIn(alunoId,
                listasDoAluno.stream().map(Lista::getId).collect(Collectors.toList()));

        metricas.setListasRespondidas(notasListas.size());

        // Calcular média de listas (nota é de 0 a 10)
        double mediaListas = notasListas.stream()
                .filter(nl -> nl.getNota() != null)
                .mapToDouble(nl -> nl.getNota().doubleValue())
                .average()
                .orElse(0.0);
        metricas.setMediaListas(Math.round(mediaListas * 100.0) / 100.0);

        // Média percentual de listas (assumindo nota máxima 10)
        double mediaPercentualListas = (mediaListas / 10.0) * 100.0;
        metricas.setMediaPercentualListas(Math.round(mediaPercentualListas * 100.0) / 100.0);

        // Detalhes das listas
        List<MetricasAlunoDTO.ListaDetalhe> listasDetalhes = listasDoAluno.stream()
                .map(lista -> {
                    MetricasAlunoDTO.ListaDetalhe detalhe = new MetricasAlunoDTO.ListaDetalhe();
                    detalhe.setListaId(lista.getId());
                    detalhe.setTitulo(lista.getTitulo());
                    detalhe.setTotalQuestoes(lista.getQuestoes() != null ? lista.getQuestoes().size() : 0);

                    // Buscar nota da lista
                    Optional<ListaEstudanteNota> notaOpt = notasListas.stream()
                            .filter(nl -> nl.getLista().getId().equals(lista.getId()))
                            .findFirst();

                    if (notaOpt.isPresent()) {
                        ListaEstudanteNota nota = notaOpt.get();
                        detalhe.setNota(nota.getNota() != null ? nota.getNota().doubleValue() : null);

                        if (nota.getNota() != null) {
                            double percentual = (nota.getNota().doubleValue() / 10.0) * 100.0;
                            detalhe.setPercentual(Math.round(percentual * 100.0) / 100.0);
                        }

                        // Contar questões respondidas
                        long questoesRespondidas = respostaEstudantesRepository
                                .countByListaIdAndEstudanteId(lista.getId(), alunoId);
                        detalhe.setQuestoesRespondidas((int) questoesRespondidas);
                    } else {
                        detalhe.setNota(null);
                        detalhe.setPercentual(0.0);
                        detalhe.setQuestoesRespondidas(0);
                    }

                    return detalhe;
                })
                .collect(Collectors.toList());
        metricas.setListas(listasDetalhes);

        // ===== MÉTRICAS GERAIS =====
        // Calcular média geral (considerando eventos e listas)
        double mediaGeral = 0.0;
        int totalAvaliacoes = 0;
        double somaNotas = 0.0;

        // Somar médias dos eventos (normalizar para escala 0-10)
        if (metricas.getMediaPercentualEventos() > 0) {
            somaNotas += (metricas.getMediaPercentualEventos() / 10.0);
            totalAvaliacoes++;
        }

        // Somar média das listas
        if (metricas.getMediaListas() > 0) {
            somaNotas += metricas.getMediaListas();
            totalAvaliacoes++;
        }

        if (totalAvaliacoes > 0) {
            mediaGeral = somaNotas / totalAvaliacoes;
        }

        metricas.setMediaGeral(Math.round(mediaGeral * 100.0) / 100.0);

        // Calcular taxa de entrega
        int totalAtividades = metricas.getTotalEventos() + metricas.getTotalListas();
        int totalEntregas = metricas.getEventosEntregues() + metricas.getListasRespondidas();
        double taxaEntrega = totalAtividades > 0 ? ((double) totalEntregas / totalAtividades) * 100.0 : 0.0;
        metricas.setTaxaEntrega(Math.round(taxaEntrega * 100.0) / 100.0);

        // Definir status baseado na média geral
        String status;
        if (mediaGeral >= 8.5) {
            status = "Excelente";
        } else if (mediaGeral >= 7.0) {
            status = "Bom";
        } else if (mediaGeral >= 5.0) {
            status = "Regular";
        } else {
            status = "Precisa Melhorar";
        }
        metricas.setStatus(status);

        return metricas;
    }
}
