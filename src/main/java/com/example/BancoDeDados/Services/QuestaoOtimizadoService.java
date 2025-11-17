package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.QuestaoAlternativa;
import com.example.BancoDeDados.Repositores.QuestaoAlternativaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.dto.QuestaoComAlternativasDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestaoOtimizadoService {

    private final QuestaoRepositores questaoRepositores;
    private final QuestaoAlternativaRepository alternativaRepository;

    /**
     * ✅ Busca questões com alternativas em 2 queries otimizadas
     * Evita N+1 problem completamente
     */
    @Transactional(readOnly = true)
    public List<QuestaoComAlternativasDTO> buscarQuestoesComAlternativasPorLista(UUID listaId) {
        // 1️⃣ Buscar questões (1 query)
        List<Questao> questoes = questaoRepositores.findByListaId(listaId);

        if (questoes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2️⃣ Buscar TODAS as alternativas em 1 query
        List<Integer> questaoIds = questoes.stream()
            .map(Questao::getId)
            .collect(Collectors.toList());

        List<QuestaoAlternativa> todasAlternativas =
            alternativaRepository.findByQuestaoIdInOrderByOrdem(questaoIds);

        // 3️⃣ Agrupar alternativas por questao_id (em memória - muito rápido)
        Map<Integer, List<QuestaoAlternativa>> alternativasPorQuestao =
            todasAlternativas.stream()
                .collect(Collectors.groupingBy(a -> a.getQuestao().getId()));

        // 4️⃣ Montar DTOs
        return questoes.stream()
            .map(q -> QuestaoComAlternativasDTO.builder()
                .id(q.getId())
                .cabecalho(q.getCabecalho())
                .enunciado(q.getEnunciado())
                .gabarito(q.getGabarito())
                .alternativas(
                    alternativasPorQuestao.getOrDefault(q.getId(), Collections.emptyList())
                        .stream()
                        .map(a -> QuestaoComAlternativasDTO.AlternativaDTO.builder()
                            .id(a.getId())
                            .ordem(a.getOrdem())
                            .texto(a.getTexto())
                            .build())
                        .collect(Collectors.toList())
                )
                .build())
            .collect(Collectors.toList());
    }

    /**
     * ✅ Busca uma questão com alternativas
     */
    @Transactional(readOnly = true)
    public Optional<QuestaoComAlternativasDTO> buscarQuestaoComAlternativasPorId(Integer id) {
        Optional<Questao> questaoOpt = questaoRepositores.findById(id);

        if (questaoOpt.isEmpty()) {
            return Optional.empty();
        }

        Questao q = questaoOpt.get();
        List<QuestaoAlternativa> alternativas =
            alternativaRepository.findByQuestaoIdOrderByOrdem(id);

        return Optional.of(QuestaoComAlternativasDTO.builder()
            .id(q.getId())
            .cabecalho(q.getCabecalho())
            .enunciado(q.getEnunciado())
            .gabarito(q.getGabarito())
            .alternativas(
                alternativas.stream()
                    .map(a -> QuestaoComAlternativasDTO.AlternativaDTO.builder()
                        .id(a.getId())
                        .ordem(a.getOrdem())
                        .texto(a.getTexto())
                        .build())
                    .collect(Collectors.toList())
            )
            .build());
    }

    /**
     * ✅ Criar questão com alternativas
     */
    @Transactional
    public Questao criarQuestaoComAlternativas(
            String cabecalho,
            String enunciado,
            List<String> alternativasTexto,
            Integer gabarito) {

        if (alternativasTexto == null || alternativasTexto.isEmpty()) {
            throw new IllegalArgumentException("A lista de alternativas não pode ser vazia.");
        }

        if (gabarito == null || gabarito < 0 || gabarito >= alternativasTexto.size()) {
            throw new IllegalArgumentException("Índice do gabarito inválido.");
        }

        Questao questao = Questao.builder()
            .cabecalho(cabecalho)
            .enunciado(enunciado)
            .gabarito(gabarito)
            .build();

        // Adicionar alternativas
        for (int i = 0; i < alternativasTexto.size(); i++) {
            questao.addAlternativa(alternativasTexto.get(i), i);
        }

        return questaoRepositores.save(questao);
    }
}

