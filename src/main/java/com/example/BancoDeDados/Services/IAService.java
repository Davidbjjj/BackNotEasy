package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Utils.TextoUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class IAService {

    private String respostaDoGemini;

    @Value("${apikey}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public String enviarParaGemini(String textoPDF) {
        RestTemplate restTemplate = new RestTemplate();

        // Divide texto em partes menores para evitar limite de entrada
        List<String> partesTexto = TextoUtils.dividirTexto(textoPDF, 12000);
        List<String> respostas = new ArrayList<>();

        for (String parte : partesTexto) {

            String prompt = "Extraia as questões do texto abaixo e formate-as no seguinte padrão JSON, mesmo que o texto original não esteja totalmente estruturado assim. O padrão é:\n" +
                    "[\n" +
                    "  {\n" +
                    "    \"cabecalho\": \"(Exemplo - 2023)\",\n" +
                    "    \"enunciado\": \"Exemplo de enunciado de questão.\",\n" +
                    "    \"alternativas\": [\n" +
                    "      \"a) Alternativa A.\",\n" +
                    "      \"b) Alternativa B.\",\n" +
                    "      \"c) Alternativa C.\",\n" +
                    "      \"d) Alternativa D.\",\n" +
                    "      \"e) Alternativa E.\"\n" +
                    "    ],\n" +
                    "    \"gabarito\": 0\n" +
                    "  }\n" +
                    "]\n" +
                    "Certifique-se de que cada questão extraída tenha os campos \"cabecalho\", \"enunciado\", uma lista de \"alternativas\", e o campo \"gabarito\". Preencha o campo \"cabecalho\" com a fonte e o ano da questão entre parênteses, mesmo que não esteja claro no texto original, e organize as alternativas no formato solicitado. O campo \"gabarito\" deve conter o índice correspondente à alternativa correta (0 para \"a\", 1 para \"b\", etc.).\n" +  parte;

            JSONObject requestPayload = new JSONObject();
            JSONObject content = new JSONObject();
            JSONObject part = new JSONObject();

            part.put("text", prompt);
            content.append("parts", part);
            requestPayload.append("contents", content);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey); // 🔹 novo header exigido

            HttpEntity<String> entity = new HttpEntity<>(requestPayload.toString(), headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        GEMINI_URL, HttpMethod.POST, entity, String.class);

                JSONObject jsonResponse = new JSONObject(response.getBody());
                JSONArray candidates = jsonResponse.optJSONArray("candidates");

                if (candidates != null && candidates.length() > 0) {
                    JSONObject contentObj = candidates.getJSONObject(0).getJSONObject("content");
                    JSONArray parts = contentObj.getJSONArray("parts");
                    if (parts.length() > 0) {
                        respostas.add(parts.getJSONObject(0).getString("text"));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Junta todas as partes em um único JSON array
        String jsonConcatenado = String.join(",", respostas);
        respostaDoGemini = "[" + jsonConcatenado + "]";
        return respostaDoGemini;
    }

    public String getRespostaDoGemini() {
        return respostaDoGemini;
    }

    public String enviarPromptSimples(String prompt) {
        RestTemplate restTemplate = new RestTemplate();
        JSONObject requestPayload = new JSONObject();
        JSONObject content = new JSONObject();
        JSONObject part = new JSONObject();
        part.put("text", prompt);
        content.append("parts", part);
        requestPayload.append("contents", content);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<String> entity = new HttpEntity<>(requestPayload.toString(), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(GEMINI_URL, HttpMethod.POST, entity, String.class);
            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONArray candidates = jsonResponse.optJSONArray("candidates");
            if (candidates != null && candidates.length() > 0) {
                JSONObject contentObj = candidates.getJSONObject(0).getJSONObject("content");
                JSONArray parts = contentObj.getJSONArray("parts");
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text");
                }
            }
        } catch (Exception e) {
            return "Erro ao consultar serviço de IA: " + e.getMessage();
        }
        return "";
    }
}
