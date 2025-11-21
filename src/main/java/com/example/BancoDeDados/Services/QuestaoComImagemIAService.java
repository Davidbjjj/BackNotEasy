package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.dto.QuestaoExtraidaDTO;
import com.example.BancoDeDados.service.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Serviço que integra extração OCR com envio para IA
 * Fluxo completo: PDF → Extração → OCR → Prompt com imagens → IA → Resposta
 */
@Service
public class QuestaoComImagemIAService {

    private static final Logger logger = LoggerFactory.getLogger(QuestaoComImagemIAService.class);

    private final FileExtractionService fileExtractionService;
    private final QuestionParserService questionParserService;
    private final PromptBuilder promptBuilder;
    private final ImagemQuestaoService imagemService;

    @Value("${apikey}")
    private String apiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public QuestaoComImagemIAService(FileExtractionService fileExtractionService,
                                     QuestionParserService questionParserService,
                                     PromptBuilder promptBuilder,
                                     ImagemQuestaoService imagemService) {
        this.fileExtractionService = fileExtractionService;
        this.questionParserService = questionParserService;
        this.promptBuilder = promptBuilder;
        this.imagemService = imagemService;
    }

    /**
     * Processa PDF com OCR e envia para IA com contexto das imagens
     */
    public Map<String, Object> processarPdfComImagensParaIA(MultipartFile file, String filePrefix)
            throws IOException {

        logger.info("Iniciando processamento de PDF com imagens para IA: {}", file.getOriginalFilename());

        // 1. Extrair texto e fazer OCR das imagens
        ExtractionResult extraction = fileExtractionService.extract(file, filePrefix);

        // 2. Montar prompt combinando texto + OCR das imagens
        String promptCompleto = montarPromptComImagens(extraction);

        logger.info("Prompt montado com {} caracteres, {} imagens processadas",
                   promptCompleto.length(), extraction.getImageOcr().size());

        // 3. Enviar para IA (Gemini)
        String respostaIA = enviarParaGemini(promptCompleto);

        // 4. Parse da resposta da IA
        List<QuestaoExtraidaDTO> questoesExtraidas = parseRespostaIA(respostaIA);

        // 5. Preparar dados de imagens para resposta
        List<Map<String, Object>> imagensInfo = new ArrayList<>();
        for (String filePath : extraction.getSavedFiles()) {
            File imgFile = new File(filePath);
            Map<String, Object> imgInfo = new HashMap<>();
            imgInfo.put("nomeArquivo", imgFile.getName());
            imgInfo.put("caminhoTemporario", filePath);
            imgInfo.put("textoOcr", extraction.getImageOcr().getOrDefault(imgFile.getName(), ""));
            imagensInfo.add(imgInfo);
        }

        // 6. Montar resposta
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("textoExtraido", extraction.getText());
        resultado.put("totalPaginas", extraction.getSavedFiles().size());
        resultado.put("questoesIA", questoesExtraidas);
        resultado.put("totalQuestoes", questoesExtraidas.size());
        resultado.put("respostaIABruta", respostaIA);
        resultado.put("promptEnviado", promptCompleto.substring(0, Math.min(500, promptCompleto.length())) + "...");
        resultado.put("imagensExtraidas", imagensInfo); // Adiciona informações das imagens
        resultado.put("arquivosTemporarios", extraction.getSavedFiles()); // Caminhos para salvar no banco
        resultado.put("textosOcr", new ArrayList<>(extraction.getImageOcr().values())); // Textos OCR ordenados

        logger.info("Processamento concluído: {} questões extraídas pela IA, {} imagens processadas",
                   questoesExtraidas.size(), extraction.getSavedFiles().size());

        return resultado;
    }

    /**
     * Monta prompt inteligente combinando texto principal + OCR das imagens
     */
    private String montarPromptComImagens(ExtractionResult extraction) {
        StringBuilder prompt = new StringBuilder();

        // Instrução para a IA
        prompt.append("Você é um especialista em extrair questões de provas e exercícios. ");
        prompt.append("Analise o texto abaixo que foi extraído de um documento usando OCR. ");
        prompt.append("Algumas partes podem ter vindo de imagens renderizadas das páginas.\n\n");

        // Texto principal
        if (extraction.getText() != null && !extraction.getText().isBlank()) {
            prompt.append("=== TEXTO PRINCIPAL DO DOCUMENTO ===\n");
            prompt.append(extraction.getText()).append("\n\n");
        }

        // OCR das imagens (ordenado por página)
        if (!extraction.getImageOcr().isEmpty()) {
            prompt.append("=== TEXTO EXTRAÍDO DAS PÁGINAS (OCR) ===\n");

            extraction.getImageOcr().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    if (!entry.getValue().isBlank()) {
                        prompt.append("\n[Página: ").append(entry.getKey()).append("]\n");
                        prompt.append(entry.getValue()).append("\n");
                    }
                });

            prompt.append("\n");
        }

        // Instruções de formatação
        prompt.append("\n=== INSTRUÇÕES ===\n");
        prompt.append("Extraia TODAS as questões encontradas e formate no seguinte padrão JSON:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"cabecalho\": \"(Fonte - Ano)\",\n");
        prompt.append("    \"enunciado\": \"Texto completo da questão\",\n");
        prompt.append("    \"alternativas\": [\n");
        prompt.append("      \"a) Texto da alternativa A\",\n");
        prompt.append("      \"b) Texto da alternativa B\",\n");
        prompt.append("      \"c) Texto da alternativa C\",\n");
        prompt.append("      \"d) Texto da alternativa D\",\n");
        prompt.append("      \"e) Texto da alternativa E\"\n");
        prompt.append("    ],\n");
        prompt.append("    \"gabarito\": 0\n");
        prompt.append("  }\n");
        prompt.append("]\n\n");
        prompt.append("IMPORTANTE:\n");
        prompt.append("- O campo 'gabarito' é o índice da alternativa correta (0=a, 1=b, 2=c, etc.)\n");
        prompt.append("- Se não souber o gabarito, use -1\n");
        prompt.append("- Mantenha a formatação exata do JSON\n");
        prompt.append("- Extraia TODAS as questões, mesmo que estejam em partes diferentes do texto\n");
        prompt.append("- Se houver texto de OCR duplicado em 'Texto Principal' e 'Páginas', use a versão mais completa\n");

        return prompt.toString();
    }

    /**
     * Envia prompt para Gemini AI
     */
    private String enviarParaGemini(String prompt) throws IOException {
        RestTemplate restTemplate = new RestTemplate();

        // Dividir se muito grande (limite Gemini ~30k tokens ≈ 100k chars)
        if (prompt.length() > 100000) {
            logger.warn("Prompt muito grande ({}), será truncado", prompt.length());
            prompt = prompt.substring(0, 100000) + "\n\n[Texto truncado devido ao tamanho]";
        }

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
            logger.info("Enviando requisição para Gemini AI...");
            ResponseEntity<String> response = restTemplate.exchange(
                    GEMINI_URL, HttpMethod.POST, entity, String.class);

            JSONObject jsonResponse = new JSONObject(response.getBody());
            JSONArray candidates = jsonResponse.optJSONArray("candidates");

            if (candidates != null && candidates.length() > 0) {
                JSONObject contentObj = candidates.getJSONObject(0).getJSONObject("content");
                JSONArray parts = contentObj.getJSONArray("parts");
                if (parts.length() > 0) {
                    String respostaIA = parts.getJSONObject(0).getString("text");
                    logger.info("Resposta recebida da IA: {} caracteres", respostaIA.length());
                    return respostaIA;
                }
            }

            throw new IOException("Resposta da IA vazia ou inválida");

        } catch (Exception e) {
            logger.error("Erro ao comunicar com Gemini AI: {}", e.getMessage());
            throw new IOException("Falha ao enviar para IA: " + e.getMessage(), e);
        }
    }

    /**
     * Faz parse da resposta JSON da IA
     */
    private List<QuestaoExtraidaDTO> parseRespostaIA(String respostaIA) {
        List<QuestaoExtraidaDTO> questoes = new ArrayList<>();

        try {
            // Extrair JSON da resposta (pode vir com markdown ```json)
            String jsonLimpo = extrairJSON(respostaIA);

            JSONArray jsonArray = new JSONArray(jsonLimpo);

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject questaoJson = jsonArray.getJSONObject(i);

                QuestaoExtraidaDTO questao = new QuestaoExtraidaDTO();
                questao.setNumero(i + 1);
                questao.setContexto(questaoJson.optString("cabecalho", ""));
                questao.setEnunciado(questaoJson.optString("enunciado", ""));

                // Parse alternativas
                JSONArray alternativas = questaoJson.optJSONArray("alternativas");
                if (alternativas != null) {
                    for (int j = 0; j < alternativas.length(); j++) {
                        String alt = alternativas.getString(j);
                        // Extrair letra (a, b, c, etc.)
                        String letra = String.valueOf((char)('a' + j));
                        if (alt.matches("^[a-eA-E]\\).*")) {
                            letra = alt.substring(0, 1).toLowerCase();
                            alt = alt.substring(2).trim();
                        }
                        questao.addAlternativa(letra, alt);
                    }
                }

                // Gabarito
                int gabaritoIdx = questaoJson.optInt("gabarito", -1);
                if (gabaritoIdx >= 0 && gabaritoIdx < 5) {
                    questao.setGabarito(String.valueOf((char)('a' + gabaritoIdx)));
                }

                questao.setConfianca(1.0); // Confiança alta - veio da IA
                questoes.add(questao);
            }

            logger.info("Parse concluído: {} questões extraídas", questoes.size());

        } catch (Exception e) {
            logger.error("Erro ao fazer parse da resposta da IA: {}", e.getMessage());
            logger.debug("Resposta recebida: {}", respostaIA);
        }

        return questoes;
    }

    /**
     * Extrai JSON de uma resposta que pode conter markdown
     */
    private String extrairJSON(String texto) {
        // Remover markdown ```json e ```
        texto = texto.replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

        // Encontrar [ e ] mais externos
        int inicio = texto.indexOf('[');
        int fim = texto.lastIndexOf(']');

        if (inicio >= 0 && fim > inicio) {
            return texto.substring(inicio, fim + 1);
        }

        return texto;
    }
}

