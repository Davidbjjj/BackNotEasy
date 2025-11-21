package com.example.BancoDeDados.Services;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para associar imagens às questões corretas baseado no contexto do texto OCR
 */
@Service
public class ImagemQuestaoAssociadorService {

    private static final Logger logger = LoggerFactory.getLogger(ImagemQuestaoAssociadorService.class);

    // Padrões para identificar questões no texto OCR
    private static final Pattern QUESTAO_PATTERN = Pattern.compile(
        "(?:Exercício|Questão|Quest[ãa]o|Pergunta|Q\\.?)\\s*(?:n[ºo°]?\\s*)?([\\d]+)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Associa cada imagem à questão correta baseado no número da questão encontrado no OCR
     *
     * @param textosOcr Textos extraídos de cada imagem (ordenados por página)
     * @param totalQuestoes Número total de questões extraídas
     * @return Map onde chave é índice da imagem e valor é número da questão (1-based)
     */
    public Map<Integer, Integer> associarImagensAQuestoes(List<String> textosOcr, int totalQuestoes) {
        Map<Integer, Integer> associacoes = new HashMap<>();

        for (int i = 0; i < textosOcr.size(); i++) {
            String textoOcr = textosOcr.get(i);

            // Tentar identificar número da questão no texto OCR
            Integer numeroQuestao = extrairNumeroQuestao(textoOcr);

            if (numeroQuestao != null && numeroQuestao <= totalQuestoes) {
                associacoes.put(i, numeroQuestao);
                logger.debug("Imagem {} associada à Questão {}", i, numeroQuestao);
            } else {
                // Se não encontrou número, usar heurística de distribuição
                int questaoEstimada = estimarQuestaoParaImagem(i, textosOcr.size(), totalQuestoes);
                associacoes.put(i, questaoEstimada);
                logger.debug("Imagem {} associada à Questão {} (estimativa)", i, questaoEstimada);
            }
        }

        logger.info("Associadas {} imagens a {} questões", associacoes.size(), totalQuestoes);
        return associacoes;
    }

    /**
     * Extrai o número da questão do texto OCR
     */
    private Integer extrairNumeroQuestao(String textoOcr) {
        if (textoOcr == null || textoOcr.isBlank()) {
            return null;
        }

        Matcher matcher = QUESTAO_PATTERN.matcher(textoOcr);

        // Pegar a primeira ocorrência (geralmente a mais relevante)
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Erro ao parsear número da questão: {}", matcher.group(1));
            }
        }

        return null;
    }

    /**
     * Estima qual questão uma imagem pertence baseado na posição da imagem
     * Assume distribuição uniforme de imagens por questões
     */
    private int estimarQuestaoParaImagem(int indiceImagem, int totalImagens, int totalQuestoes) {
        // Distribuição simples: divide imagens igualmente entre questões
        double imagensPorQuestao = (double) totalImagens / totalQuestoes;
        int questaoEstimada = (int) Math.ceil((indiceImagem + 1) / imagensPorQuestao);

        // Garantir que está no range válido
        return Math.max(1, Math.min(questaoEstimada, totalQuestoes));
    }

    /**
     * Agrupa índices de imagens por número de questão
     *
     * @param textosOcr Textos OCR das imagens
     * @param totalQuestoes Total de questões
     * @return Map onde chave é número da questão e valor é lista de índices de imagens
     */
    public Map<Integer, List<Integer>> agruparImagensPorQuestao(List<String> textosOcr, int totalQuestoes) {
        Map<Integer, Integer> associacoes = associarImagensAQuestoes(textosOcr, totalQuestoes);
        Map<Integer, List<Integer>> grupos = new HashMap<>();

        // Inicializar listas para cada questão
        for (int i = 1; i <= totalQuestoes; i++) {
            grupos.put(i, new ArrayList<>());
        }

        // Agrupar índices de imagens por questão
        for (Map.Entry<Integer, Integer> entry : associacoes.entrySet()) {
            int indiceImagem = entry.getKey();
            int numeroQuestao = entry.getValue();
            grupos.get(numeroQuestao).add(indiceImagem);
        }

        // Log de debug
        grupos.forEach((questao, imagens) -> {
            if (!imagens.isEmpty()) {
                logger.info("Questão {} tem {} imagem(ns): {}", questao, imagens.size(), imagens);
            }
        });

        return grupos;
    }

    /**
     * Verifica se uma imagem contém referência a uma questão específica
     */
    public boolean imagemPertenceAQuestao(String textoOcr, int numeroQuestao) {
        Integer numeroEncontrado = extrairNumeroQuestao(textoOcr);
        return numeroEncontrado != null && numeroEncontrado.equals(numeroQuestao);
    }

    /**
     * Calcula score de relevância de uma imagem para uma questão baseado no texto
     * Útil para casos ambíguos
     */
    public double calcularRelevancia(String textoOcr, String enunciadoQuestao) {
        if (textoOcr == null || textoOcr.isBlank() || enunciadoQuestao == null) {
            return 0.0;
        }

        // Normalizar textos
        String ocrNorm = textoOcr.toLowerCase().trim();
        String enunciadoNorm = enunciadoQuestao.toLowerCase().trim();

        // Extrair palavras-chave do enunciado (> 4 caracteres)
        Set<String> palavrasChave = new HashSet<>();
        for (String palavra : enunciadoNorm.split("\\s+")) {
            if (palavra.length() > 4) {
                palavrasChave.add(palavra);
            }
        }

        // Contar quantas palavras-chave aparecem no OCR
        int matches = 0;
        for (String palavra : palavrasChave) {
            if (ocrNorm.contains(palavra)) {
                matches++;
            }
        }

        // Score normalizado
        return palavrasChave.isEmpty() ? 0.0 : (double) matches / palavrasChave.size();
    }
}

