package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.dto.QuestaoExtraidaDTO;
import com.example.BancoDeDados.service.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
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

    private final ModeloQuestoesService modeloQuestoesService;

    public QuestaoComImagemIAService(FileExtractionService fileExtractionService,
                                     QuestionParserService questionParserService,
                                     PromptBuilder promptBuilder,
                                     ImagemQuestaoService imagemService,
                                     ModeloQuestoesService modeloQuestoesService) {
        this.fileExtractionService = fileExtractionService;
        this.questionParserService = questionParserService;
        this.promptBuilder = promptBuilder;
        this.imagemService = imagemService;
        this.modeloQuestoesService = modeloQuestoesService;
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

        // 3. Enviar para o modelo (modelo.joblib)
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

        // OCR e posição na mesma ordem de arquivosTemporarios (o mapa de OCR não preserva a ordem)
        List<String> textosOcr = new ArrayList<>();
        List<PosicaoImagem> posicoes = new ArrayList<>();
        for (String filePath : extraction.getSavedFiles()) {
            String nome = new File(filePath).getName();
            textosOcr.add(extraction.getImageOcr().getOrDefault(nome, ""));
            posicoes.add(extraction.getImagePositions().get(nome));
        }
        resultado.put("textosOcr", textosOcr);
        resultado.put("posicoesImagens", posicoes);
        resultado.put("linhasPdf", extraction.getPdfLines());

        logger.info("Processamento concluído: {} questões extraídas pela IA, {} imagens processadas",
                   questoesExtraidas.size(), extraction.getSavedFiles().size());

        return resultado;
    }

    /**
     * Monta o texto a ser analisado pelo modelo: texto principal do documento
     * seguido do OCR das imagens (ordenado pelo nome do arquivo).
     */
    private String montarPromptComImagens(ExtractionResult extraction) {
        StringBuilder texto = new StringBuilder();

        if (extraction.getText() != null && !extraction.getText().isBlank()) {
            texto.append(extraction.getText()).append("\n\n");
        }

        String textoPrincipal = extraction.getText();
        extraction.getImageOcr().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String ocr = entry.getValue();
                if (ocr == null || ocr.isBlank()) return;
                // Páginas sem imagem embutida são renderizadas e passam pelo OCR: se o texto
                // do PDF já contém esse conteúdo, anexá-lo duplicaria as questões.
                if (jaContidoNoTexto(ocr, textoPrincipal)) {
                    logger.info("OCR de {} ignorado: conteúdo já presente no texto do PDF", entry.getKey());
                    return;
                }
                texto.append(ocr).append("\n\n");
            });

        return texto.toString();
    }

    /** True se (quase) todas as palavras do OCR já aparecem no texto principal. */
    private boolean jaContidoNoTexto(String ocr, String principal) {
        if (principal == null || principal.isBlank()) return false;
        java.util.Set<String> palavrasPrincipal = new java.util.HashSet<>(
                java.util.Arrays.asList(principal.toLowerCase().split("[^\\p{L}\\p{N}]+")));
        String[] palavrasOcr = ocr.toLowerCase().split("[^\\p{L}\\p{N}]+");
        int total = 0, presentes = 0;
        for (String p : palavrasOcr) {
            if (p.isEmpty()) continue;
            total++;
            if (palavrasPrincipal.contains(p)) presentes++;
        }
        return total > 0 && presentes >= total * 0.9;
    }

    /**
     * Envia o texto ao modelo (modelo.joblib via ml-service).
     * Devolve um JSON array: [{cabecalho, enunciado, alternativas[], gabarito (índice ou null)}]
     */
    private String enviarParaGemini(String texto) throws IOException {
        logger.info("Enviando texto ({} caracteres) para o modelo de questões...", texto.length());
        String resposta = modeloQuestoesService.extrairQuestoes(texto).toString();
        logger.info("Resposta recebida do modelo: {} caracteres", resposta.length());
        return resposta;
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

                // Gabarito (null quando o texto não trouxe)
                if (!questaoJson.isNull("gabarito")) {
                    int gabaritoIdx = questaoJson.optInt("gabarito", -1);
                    if (gabaritoIdx >= 0 && gabaritoIdx < 5) {
                        questao.setGabarito(String.valueOf((char)('a' + gabaritoIdx)));
                    }
                }

                questao.setConfianca(questaoJson.optDouble("probabilidade", 1.0));
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

