package com.example.BancoDeDados.Services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiService {

    @Autowired
    private IAService iaService;

    private static final int MAX_CHUNK = 6000; // tamanho seguro abaixo do limite da API

    public String gerarResposta(String prompt) {
        if (prompt == null || prompt.isBlank()) return "";
        List<String> partes = dividirPrompt(prompt, MAX_CHUNK);
        List<String> respostas = new ArrayList<>();
        int index = 1;
        for (String p : partes) {
            String cabecalho = "Parte " + index + "/" + partes.size() + ":\n";
            String respostaParte = iaService.enviarPromptSimples(cabecalho + p);
            respostas.add(respostaParte);
            index++;
        }
        // Estratégia de junção: pede à IA que consolide se houver mais de uma parte
        if (respostas.size() == 1) {
            return respostas.get(0);
        }
        String consolidacaoPrompt = montarPromptConsolidacao(respostas);
        String consolidado = iaService.enviarPromptSimples(consolidacaoPrompt);
        return consolidado != null && !consolidado.isBlank() ? consolidado : String.join("\n---\n", respostas);
    }

    private List<String> dividirPrompt(String texto, int tamanhoMax) {
        List<String> partes = new ArrayList<>();
        if (texto.length() <= tamanhoMax) {
            partes.add(texto);
            return partes;
        }
        int inicio = 0;
        while (inicio < texto.length()) {
            int fim = Math.min(inicio + tamanhoMax, texto.length());
            if (fim < texto.length()) {
                int ultimoQuebra = texto.lastIndexOf('\n', fim);
                if (ultimoQuebra > inicio + (tamanhoMax / 3)) { // evita partes muito pequenas
                    fim = ultimoQuebra;
                }
            }
            partes.add(texto.substring(inicio, fim));
            inicio = fim;
            while (inicio < texto.length() && (texto.charAt(inicio) == '\n' || texto.charAt(inicio) == '\r')) inicio++;
        }
        return partes;
    }

    private String montarPromptConsolidacao(List<String> respostasParciais) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você recebeu várias partes de uma análise/geração anterior. Consolide-as em uma única resposta final seguindo o formato já solicitado (mantendo apenas os campos esperados).\n\n");
        for (int i = 0; i < respostasParciais.size(); i++) {
            sb.append("[Parte ").append(i + 1).append("]\n");
            sb.append(respostasParciais.get(i)).append("\n\n");
        }
        sb.append("Retorne somente o JSON final consolidado sem explicações extras.");
        return sb.toString();
    }
}
