package com.example.BancoDeDados.Utils;

import java.util.ArrayList;
import java.util.List;

public class TextoUtils {

    public static List<String> dividirTexto(String texto, int tamanhoMaximo) {
        List<String> partes = new ArrayList<>();

        if (texto == null || texto.length() <= tamanhoMaximo) {
            partes.add(texto);
            return partes;
        }

        int inicio = 0;
        while (inicio < texto.length()) {
            int fim = Math.min(inicio + tamanhoMaximo, texto.length());

            // Tenta quebrar em um ponto "natural" (final de linha ou parágrafo)
            if (fim < texto.length()) {
                int ultimoPonto = texto.lastIndexOf('\n', fim);
                if (ultimoPonto > inicio) {
                    fim = ultimoPonto;
                }
            }

            partes.add(texto.substring(inicio, fim));
            inicio = fim;

            // Pula quebras de linha
            while (inicio < texto.length() &&
                    (texto.charAt(inicio) == '\n' || texto.charAt(inicio) == '\r')) {
                inicio++;
            }
        }

        return partes;
    }
}