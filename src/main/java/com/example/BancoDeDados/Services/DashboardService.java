package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.RespostaEstudantes;
import com.example.BancoDeDados.Repositores.ListaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ListaRepository listaRepository;

    @Autowired
    public DashboardService(ListaRepository listaRepository) {
        this.listaRepository = listaRepository;
    }

    public Map<String, Object> getDashboardByListaId(Long listaId) {
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
}
