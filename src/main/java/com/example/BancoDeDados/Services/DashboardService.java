package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.Model.RespostaEstudantes;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.NotaEventoRepository;
import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;

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

    @Autowired
    public DashboardService(
            ListaRepository listaRepository,
            NotaEventoRepository notaEventoRepository,
            RespostaEstudantesRepository respostaEstudantesRepository) {
        this.listaRepository = listaRepository;
        this.notaEventoRepository = notaEventoRepository;
        this.respostaEstudantesRepository = respostaEstudantesRepository;
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
    
}
