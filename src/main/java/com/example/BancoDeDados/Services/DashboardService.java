package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.NotaEventoRepository;
import com.example.BancoDeDados.Repositores.NotaRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class DashboardService {

    private final ListaRepository listaRepository;
    private final NotaEventoRepository notaEventoRepository;
    private final EstudanteRepositores estudanteRepositores;
    private final ProfessorRepositores professorRepositores;
    private final DisciplinaRepository disciplinaRepository;
    private final NotaRepository notaRepository;

    @Autowired
    public DashboardService(ListaRepository listaRepository,
                            NotaEventoRepository notaEventoRepository,
                            EstudanteRepositores estudanteRepositores,
                            ProfessorRepositores professorRepositores,
                            DisciplinaRepository disciplinaRepository,
                            NotaRepository notaRepository) {
        this.listaRepository = listaRepository;
        this.notaEventoRepository = notaEventoRepository;
        this.estudanteRepositores = estudanteRepositores;
        this.professorRepositores = professorRepositores;
        this.disciplinaRepository = disciplinaRepository;
        this.notaRepository = notaRepository;
    }

    public Map<String, Object> getDashboardByListaId(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("tituloLista", lista.getTitulo());
        dashboard.put("professor", lista.getProfessor().getNome());

        List<Map<String, Object>> questoesData = lista.getQuestoes().stream().map(questao -> {
            Map<String, Object> questaoInfo = new HashMap<>();
            questaoInfo.put("id", questao.getId());
            questaoInfo.put("enunciado", questao.getEnunciado());

            Integer gabarito = questao.getGabarito();
            questaoInfo.put("gabarito", gabarito);

            List<Map<String, Object>> respostasEstudantes = questao.getRespostasEstudantes().stream().map(resposta -> {
                Map<String, Object> respostaInfo = new HashMap<>();
                respostaInfo.put("estudante", resposta.getEstudante().getNome());
                respostaInfo.put("respostaDada", resposta.getResposta());

                return respostaInfo;
            }).collect(Collectors.toList());

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
    
    public Map<String, Object> getMetricasPorDisciplina(LocalDateTime startDate,
                                                        LocalDateTime endDate,
                                                        UUID disciplinaId) {
        List<Object[]> rows = notaEventoRepository.findMetricasPorDisciplina(startDate, endDate, disciplinaId);

        List<Map<String, Object>> metricas = rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("disciplinaId", r[0]);
            m.put("disciplina", r[1]);
            m.put("desempenhoMedio", r[2] != null ? r[2] : 0.0);
            m.put("alunosAtivos", r[3] != null ? r[3] : 0L);
            m.put("totalListas", r[4] != null ? r[4] : 0L);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("inicio", startDate);
        dashboard.put("fim", endDate);
        dashboard.put("materiaId", disciplinaId);
        dashboard.put("metricas", metricas);
        return dashboard;
    }
    
    public Map<String, Object> getMetricasInstitucionais() {
        long totalAlunos = estudanteRepositores.count();
        long totalProfessores = professorRepositores.count();
        long totalDisciplinas = disciplinaRepository.count();
        Double mediaGlobal = notaRepository.calcularMediaGeral();

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("totalAlunos", totalAlunos);
        dashboard.put("totalProfessores", totalProfessores);
        dashboard.put("totalDisciplinas", totalDisciplinas);
        dashboard.put("mediaGlobalNotas", mediaGlobal != null ? mediaGlobal : 0.0);
        return dashboard;
    }

}
