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

    public Questao criarQuestao(String cabecalho, String enunciado, List<String> alternativas, Integer gabarito,
                                String assunto, String explicacao) {
        Questao q = criarQuestao(cabecalho, enunciado, alternativas, gabarito);
        if (assunto == null && explicacao == null) return q;
        q.setAssunto(assunto);
        q.setExplicacao(explicacao);
        return questaoRepositores.save(q);
    }

    /**
     * Cria questão objetiva ou aberta já dentro da lista (toda questão pertence a uma lista), com a
     * classificação do banco de questões (FE-201/202).
     */
    @org.springframework.transaction.annotation.Transactional
    public Questao criarQuestao(QuestaoRequestDTO req, Lista lista) {
        if (req.getEnunciado() == null || req.getEnunciado().isBlank()) {
            throw new IllegalArgumentException("Informe o enunciado da questão.");
        }
        boolean aberta = "ABERTA".equalsIgnoreCase(req.getTipo());
        Questao q = Questao.builder().cabecalho(req.getCabecalho()).enunciado(req.getEnunciado()).lista(lista).build();
        if (aberta) {
            q.setTipo("ABERTA");
        } else {
            List<String> alternativas = req.getAlternativas();
            if (alternativas == null || alternativas.size() < 2) {
                throw new IllegalArgumentException("Informe ao menos duas alternativas.");
            }
            if (req.getGabarito() == null || req.getGabarito() < 0 || req.getGabarito() >= alternativas.size()) {
                throw new IllegalArgumentException("Índice do gabarito inválido.");
            }
            q.setGabarito(req.getGabarito());
            q.setAlternativasTexto(alternativas);
        }
        q.setAssunto(req.getAssunto());
        q.setExplicacao(req.getExplicacao());
        aplicarClassificacao(q, req);
        return questaoRepositores.save(q);
    }

    /** Tipo, rubrica, dificuldade e tags; campos nulos no pedido não apagam o que já existe. */
    private void aplicarClassificacao(Questao q, QuestaoRequestDTO req) {
        if (req.getTipo() != null) q.setTipo("ABERTA".equalsIgnoreCase(req.getTipo()) ? "ABERTA" : "OBJETIVA");
        if (req.getRubrica() != null) q.setRubrica(req.getRubrica());
        if (req.getDificuldade() != null) q.setDificuldade(normalizarDificuldade(req.getDificuldade()));
        if (req.getTags() != null) q.setTags(normalizarTags(req.getTags()));
    }

    public static String normalizarDificuldade(String d) {
        if (d == null || d.isBlank()) return null;
        String v = d.trim().toUpperCase().replace("É", "E");
        if (!List.of("FACIL", "MEDIA", "DIFICIL").contains(v)) {
            throw new IllegalArgumentException("Dificuldade inválida: use FACIL, MEDIA ou DIFICIL.");
        }
        return v;
    }

    public static java.util.Set<String> normalizarTags(java.util.Collection<String> tags) {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String t : tags) {
            if (t == null) continue;
            String v = t.trim().toLowerCase();
            if (!v.isEmpty()) out.add(v.length() > 60 ? v.substring(0, 60) : v);
        }
        if (out.size() > 20) throw new IllegalArgumentException("No máximo 20 tags por questão.");
        return out;
    }

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
        if (req.getTipo() != null) questao.setTipo("ABERTA".equalsIgnoreCase(req.getTipo()) ? "ABERTA" : "OBJETIVA");
        if (!questao.isAberta()) {
            // ✅ Usa helper para atualizar alternativas
            questao.setAlternativasTexto(req.getAlternativas());
            questao.setGabarito(req.getGabarito());
        }
        if (req.getRubrica() != null) questao.setRubrica(req.getRubrica());
        if (req.getDificuldade() != null) questao.setDificuldade(normalizarDificuldade(req.getDificuldade()));
        if (req.getTags() != null) questao.setTags(normalizarTags(req.getTags()));
        // Só altera se enviados, para clientes antigos não apagarem esses campos
        if (req.getAssunto() != null) questao.setAssunto(req.getAssunto());
        if (req.getExplicacao() != null) questao.setExplicacao(req.getExplicacao());
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
