package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.ResponseDTO.QuestaoDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoRequestDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class QuestaoService {

    @Autowired
    private QuestaoRepositores questaoRepositores;

    @Autowired
    private ListaRepository listaRepository;

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
        var questao = questaoRepositores.findById(id).orElse(null);
        if (questao == null) return null;
        return new QuestaoResponseDTO(questao.getId(), questao.getCabecalho(), questao.getEnunciado(), questao.getAlternativas(), questao.getGabarito());
    }

    public List<QuestaoDTO> listarQuestoes() {
        return questaoRepositores.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private QuestaoDTO convertToDTO(Questao questao) {
        QuestaoDTO dto = new QuestaoDTO();
        dto.setId(questao.getId());
        dto.setCabecalho(questao.getCabecalho());
        dto.setEnunciado(questao.getEnunciado());
        dto.setAlternativas(questao.getAlternativas());
        dto.setGabarito(questao.getGabarito());
        if (questao.getLista() != null) dto.setTituloLista(questao.getLista().getTitulo());
        return dto;
    }

    public void deletarQuestao(Integer id) { questaoRepositores.deleteById(id); }

    public List<Questao> salvarQuestoes(List<Questao> questoes) { questaoRepositores.saveAll(questoes); return questoes; }

    public QuestaoResponseDTO atualizarQuestaoDaLista(UUID listaId, Integer questaoId, QuestaoRequestDTO req) {
        var questao = questaoRepositores.findById(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        if (questao.getLista() == null || questao.getLista().getId() == null || !questao.getLista().getId().equals(listaId)) {
            throw new RuntimeException("Questão não pertence à lista informada");
        }
        questao.setCabecalho(req.getCabecalho());
        questao.setEnunciado(req.getEnunciado());
        questao.setAlternativas(req.getAlternativas());
        questao.setGabarito(req.getGabarito());
        var salva = questaoRepositores.save(questao);
        return new QuestaoResponseDTO(salva.getId(), salva.getCabecalho(), salva.getEnunciado(), salva.getAlternativas(), salva.getGabarito());
    }

    public void deletarQuestaoDaLista(UUID listaId, Integer questaoId) {
        var questao = questaoRepositores.findById(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        if (questao.getLista() == null || questao.getLista().getId() == null || !questao.getLista().getId().equals(listaId)) {
            throw new RuntimeException("Questão não pertence à lista informada");
        }
        questaoRepositores.delete(questao);
    }

    public QuestaoResponseDTO associarQuestaoALista(UUID listaId, Integer questaoId) {
        var lista = listaRepository.findById(listaId).orElseThrow(() -> new RuntimeException("Lista não encontrada"));
        var questao = questaoRepositores.findById(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        questao.setLista(lista);
        var salva = questaoRepositores.save(questao);
        return new QuestaoResponseDTO(salva.getId(), salva.getCabecalho(), salva.getEnunciado(), salva.getAlternativas(), salva.getGabarito());
    }
}
