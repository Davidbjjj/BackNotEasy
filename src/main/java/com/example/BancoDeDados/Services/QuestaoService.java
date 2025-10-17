package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.ResponseDTO.QuestaoDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class QuestaoService {

    @Autowired
    private QuestaoRepositores questaoRepositores;

    public Questao criarQuestao(String cabecalho, String enunciado, List<String> alternativas, Integer gabarito) {
        if (alternativas == null || alternativas.isEmpty()) {
            throw new IllegalArgumentException("A lista de alternativas não pode ser vazia.");
        }

        if (gabarito == null || gabarito < 0 || gabarito >= alternativas.size()) {
            throw new IllegalArgumentException("Índice do gabarito inválido.");
        }

        Questao novaQuestao = new Questao();
        novaQuestao.setCabecalho(cabecalho);
        novaQuestao.setEnunciado(enunciado);
        novaQuestao.setAlternativas(alternativas);
        novaQuestao.setGabarito(gabarito);
        return questaoRepositores.save(novaQuestao);
    }

    public QuestaoResponseDTO buscarQuestaoPorIdComGabaritoDTO(Integer id) {
        Questao questao = questaoRepositores.findById(id).orElse(null);

        if (questao == null) {
            return null;
        }

        String alternativaCorreta = questao.getAlternativas().get(questao.getGabarito());

        return new QuestaoResponseDTO(
                questao.getId(),
                questao.getCabecalho(),
                questao.getEnunciado(),
                questao.getAlternativas(),
                questao.getGabarito()
        );
    }

    public Questao buscarQuestaoPorId(Integer id) {
        return questaoRepositores.findById(id).orElse(null);
    }

    public List<QuestaoDTO> listarQuestoes() {
        List<Questao> questoes = questaoRepositores.findAll();
        return questoes.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private QuestaoDTO convertToDTO(Questao questao) {
        QuestaoDTO dto = new QuestaoDTO();
        dto.setId(questao.getId());
        dto.setCabecalho(questao.getCabecalho());
        dto.setEnunciado(questao.getEnunciado());
        dto.setAlternativas(questao.getAlternativas());
        dto.setGabarito(questao.getGabarito());
        if (questao.getLista() != null) {
            dto.setTituloLista(questao.getLista().getTitulo());
        }

        return dto;
    }

    public void deletarQuestao(Integer id) {
        questaoRepositores.deleteById(id);
    }

    public List<Questao> salvarQuestoes(List<Questao> questoes) {
        questaoRepositores.saveAll(questoes);
        return questoes;
    }
}
