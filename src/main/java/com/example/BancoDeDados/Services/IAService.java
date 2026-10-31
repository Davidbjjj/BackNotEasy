package com.example.BancoDeDados.Services;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
public class IAService {

    private String respostaDoGemini;

    @Autowired
    private ModeloQuestoesService modeloQuestoesService;

    // Ainda usada por enviarPromptSimples (análises de desempenho), que o modelo não cobre
    @Value("${apikey}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    /**
     * Extrai as questões do texto usando o modelo local (modelo.joblib, via ml-service)
     * e devolve um JSON array: [{cabecalho, enunciado, alternativas[], gabarito}].
     * "gabarito" é o índice da alternativa correta (0=a, 1=b...) ou null quando desconhecido.
     * O nome do método foi mantido para não quebrar os chamadores.
     */
    public String enviarParaGemini(String textoPDF) {
        JSONArray questoes;
        try {
            questoes = modeloQuestoesService.extrairQuestoes(textoPDF);
        } catch (Exception e) {
            e.printStackTrace();
            questoes = new JSONArray();
        }
        respostaDoGemini = questoes.toString();
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
