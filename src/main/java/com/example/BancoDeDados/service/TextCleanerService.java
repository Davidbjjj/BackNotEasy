package com.example.BancoDeDados.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Serviço para limpeza e normalização de texto extraído por OCR
 */
@Service
public class TextCleanerService {

    private static final Pattern MULTIPLE_NEWLINES = Pattern.compile("\n{3,}");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile(" {2,}");
    private static final Pattern WHITESPACE_BEFORE_PUNCTUATION = Pattern.compile("\\s+([.,;:!?)])");

    /**
     * Limpa texto extraído removendo caracteres extras e normalizando espaçamento
     */
    public String cleanText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text;

        // 1. Normalizar quebras de linha (substituir múltiplas por dupla)
        cleaned = MULTIPLE_NEWLINES.matcher(cleaned).replaceAll("\n\n");

        // 2. Remover espaços múltiplos
        cleaned = MULTIPLE_SPACES.matcher(cleaned).replaceAll(" ");

        // 3. Remover espaços antes de pontuação
        cleaned = WHITESPACE_BEFORE_PUNCTUATION.matcher(cleaned).replaceAll("$1");

        // 4. Remover espaços no início e fim de cada linha
        cleaned = cleaned.lines()
                .map(String::trim)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        // 5. Normalizar caracteres especiais comuns de OCR
        cleaned = cleaned.replace("\u2018", "'") // ' esquerda
                         .replace("\u2019", "'") // ' direita
                         .replace("\u201C", "\"") // " esquerda
                         .replace("\u201D", "\"") // " direita
                         .replace("\u2014", "-") // em dash
                         .replace("\u2013", "-"); // en dash

        // 6. Remover caracteres de controle (exceto \n e \t)
        cleaned = cleaned.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");

        return cleaned.trim();
    }

    /**
     * Remove marcadores HTML/XML do texto
     */
    public String removeHtmlTags(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "");
    }

    /**
     * Remove URLs do texto
     */
    public String removeUrls(String text) {
        if (text == null) return "";
        return text.replaceAll("https?://[^\\s]+", "");
    }

    /**
     * Limpeza completa: aplica todas as transformações
     */
    public String fullClean(String text) {
        String cleaned = cleanText(text);
        cleaned = removeHtmlTags(cleaned);
        cleaned = removeUrls(cleaned);
        return cleaned;
    }

    /**
     * Normaliza texto de alternativas de questões (a), b), c)...)
     */
    public String normalizeAlternatives(String text) {
        if (text == null) return "";

        // Normalizar formatos: "a )" -> "a)" e "( a )" -> "a)"
        return text.replaceAll("([a-eA-E])\\s*\\)", "$1)")
                   .replaceAll("\\(\\s*([a-eA-E])\\s*\\)", "$1)");
    }

    /**
     * Remove cabeçalhos e rodapés comuns de documentos
     */
    public String removeHeadersFooters(String text) {
        if (text == null) return "";

        // Padrões comuns de rodapé/cabeçalho
        String cleaned = text;

        // Remover linhas com "Página X de Y"
        cleaned = cleaned.replaceAll("(?im)^.*página\\s+\\d+\\s+(de|/)\\s+\\d+.*$\n?", "");

        // Remover linhas com apenas números (números de página)
        cleaned = cleaned.replaceAll("(?m)^\\s*\\d+\\s*$\n?", "");

        // Remover "Remover anúncios" repetitivo
        cleaned = cleaned.replaceAll("(?im)^.*remover anúncios.*$\n?", "");

        // Remover URLs duplicadas
        cleaned = cleaned.replaceAll("(?im)^.*https?://.*$\n?", "");

        return cleaned;
    }
}

