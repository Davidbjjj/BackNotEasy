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

        // ✅ Usando builder pattern e helper method
        Questao novaQuestao = Questao.builder()
            .cabecalho(cabecalho)
            .enunciado(enunciado)
            .gabarito(gabarito)
            .build();

        // ✅ Usa helper para adicionar alternativas
        novaQuestao.setAlternativasTexto(alternativas);

        return questaoRepositores.save(novaQuestao);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public QuestaoResponseDTO buscarQuestaoPorIdComGabaritoDTO(Integer id) {
        var questao = questaoRepositores.findByIdWithAlternativas(id).orElse(null);
        if (questao == null) return null;
        // ✅ Usa helper para obter alternativas como List<String>
        return new QuestaoResponseDTO(questao.getId(), questao.getCabecalho(), questao.getEnunciado(), questao.getAlternativasTexto(), questao.getGabarito());
    }

    public List<QuestaoDTO> listarQuestoes() {
        return questaoRepositores.findAll().stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    private QuestaoDTO convertToDTO(Questao questao) {
        // DEBUG: Log para investigar dados
        System.out.println("DEBUG convertToDTO - ID: " + questao.getId() +
                         ", Cabecalho: '" + questao.getCabecalho() + "'" +
                         ", Enunciado: '" + questao.getEnunciado() + "'");

        QuestaoDTO dto = new QuestaoDTO();
        dto.setId(questao.getId());
        dto.setCabecalho(questao.getCabecalho());
        dto.setEnunciado(questao.getEnunciado());
        // ✅ Usa helper para obter alternativas como List<String>
        dto.setAlternativas(questao.getAlternativasTexto());
        dto.setGabarito(questao.getGabarito());
        if (questao.getLista() != null) dto.setTituloLista(questao.getLista().getTitulo());
        return dto;
    }

    public void deletarQuestao(Integer id) { questaoRepositores.deleteById(id); }

    public List<Questao> salvarQuestoes(List<Questao> questoes) { questaoRepositores.saveAll(questoes); return questoes; }

    @org.springframework.transaction.annotation.Transactional
    public QuestaoResponseDTO atualizarQuestaoDaLista(UUID listaId, Integer questaoId, QuestaoRequestDTO req) {
        var questao = questaoRepositores.findByIdWithAlternativas(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        if (questao.getLista() == null || questao.getLista().getId() == null || !questao.getLista().getId().equals(listaId)) {
            throw new RuntimeException("Questão não pertence à lista informada");
        }
        questao.setCabecalho(req.getCabecalho());
        questao.setEnunciado(req.getEnunciado());
        // ✅ Usa helper para atualizar alternativas
        questao.setAlternativasTexto(req.getAlternativas());
        questao.setGabarito(req.getGabarito());
        var salva = questaoRepositores.save(questao);
        return new QuestaoResponseDTO(salva.getId(), salva.getCabecalho(), salva.getEnunciado(), salva.getAlternativasTexto(), salva.getGabarito());
    }

    public void deletarQuestaoDaLista(UUID listaId, Integer questaoId) {
        var questao = questaoRepositores.findById(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        if (questao.getLista() == null || questao.getLista().getId() == null || !questao.getLista().getId().equals(listaId)) {
            throw new RuntimeException("Questão não pertence à lista informada");
        }
        questaoRepositores.delete(questao);
    }

    @org.springframework.transaction.annotation.Transactional
    public QuestaoResponseDTO associarQuestaoALista(UUID listaId, Integer questaoId) {
        var lista = listaRepository.findById(listaId).orElseThrow(() -> new RuntimeException("Lista não encontrada"));
        var questao = questaoRepositores.findByIdWithAlternativas(questaoId).orElseThrow(() -> new RuntimeException("Questão não encontrada"));
        questao.setLista(lista);
        var salva = questaoRepositores.save(questao);
        return new QuestaoResponseDTO(salva.getId(), salva.getCabecalho(), salva.getEnunciado(), salva.getAlternativasTexto(), salva.getGabarito());
    }
}
