package com.example.BancoDeDados.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lê as alternativas de uma imagem em que elas aparecem como texto (ex.: o quadro "A) 135 000,00.
 * B) 1 350 000,00. ..." de uma questão do ENEM), a partir do texto que o OCR devolveu.
 *
 * É propositalmente conservador: o OCR erra de um jeito que parece válido (a potência 1·10⁷ vira
 * "1107"; o expoente 4 vira "�"), e uma alternativa errada é pior que nenhuma. Só aceita o texto
 * quando há exatamente cinco linhas A) a E) e nenhuma delas parece notação científica ou traz
 * caracteres estranhos. Nos demais casos a imagem continua anexada à questão para o professor ver.
 */
public final class AlternativasOcrParser {

    /** "A) texto": marcador de até 2 caracteres (o OCR lê "B)" como "8)" e "E)" como "�)") + ")" + conteúdo. */
    private static final Pattern LINHA = Pattern.compile("^\\S{1,2}\\)\\s*(\\S.*)$");
    /** Só letras, dígitos, espaço e pontuação/operadores comuns de alternativa numérica ou curta. */
    private static final Pattern CARACTERES_OK = Pattern.compile("^[\\p{L}\\p{N}\\s.,;:()%$+\\-−·×/=<>²³√]+$");
    /** "número × 10..." ou "10 × ...": potência de dez cujo expoente o OCR perde ou troca. */
    private static final Pattern POTENCIA_DE_DEZ = Pattern.compile("\\d\\s*[-−·.:×*xX]\\s*10(?!\\d{3})|\\b10\\s*[-−·.×*xX]\\s*\\d");

    private AlternativasOcrParser() {
    }

    /** @return as cinco alternativas (só o texto, sem o "A)"), na ordem A..E; null se não for confiável. */
    public static List<String> parse(String ocr) {
        if (ocr == null || ocr.isBlank()) return null;
        List<String> linhas = new ArrayList<>();
        for (String l : ocr.split("\\R")) {
            if (!l.isBlank()) linhas.add(l.strip());
        }
        if (linhas.size() != 5) return null;

        List<String> textos = new ArrayList<>();
        for (String l : linhas) {
            var m = LINHA.matcher(l);
            if (!m.matches()) return null;
            String t = m.group(1).replaceAll("\\s+", " ").strip();
            if (!CARACTERES_OK.matcher(t).matches() || POTENCIA_DE_DEZ.matcher(t).find()) return null;
            textos.add(t);
        }
        // Cinco iguais = leitura quebrada, não alternativas
        if (textos.stream().distinct().count() < 4) return null;
        return textos;
    }
}
