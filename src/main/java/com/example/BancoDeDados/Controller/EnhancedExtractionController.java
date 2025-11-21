package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.dto.QuestaoExtraidaDTO;
import com.example.BancoDeDados.service.ExtractionResult;
import com.example.BancoDeDados.service.FileExtractionService;
import com.example.BancoDeDados.service.QuestionParserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/extract")
public class EnhancedExtractionController {

    private final FileExtractionService fileExtractionService;
    private final QuestionParserService questionParserService;

    public EnhancedExtractionController(FileExtractionService fileExtractionService,
                                       QuestionParserService questionParserService) {
        this.fileExtractionService = fileExtractionService;
        this.questionParserService = questionParserService;
    }

    /**
     * Endpoint otimizado que retorna questões já parseadas
     * POST /extract/questions
     */
    @PostMapping("/questions")
    public ResponseEntity<Map<String, Object>> extractQuestions(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePrefix", required = false) String filePrefix,
            @RequestParam(value = "parseQuestions", defaultValue = "true") boolean parseQuestions) {

        try {
            // 1. Extrair texto e fazer OCR (paralelo)
            ExtractionResult extraction = fileExtractionService.extract(file, filePrefix);

            // 2. Combinar texto principal + OCR das imagens
            String textoCompleto = combinarTextos(extraction);

            // 3. Parse de questões (se solicitado)
            List<QuestaoExtraidaDTO> questoes = null;
            if (parseQuestions) {
                questoes = questionParserService.parseQuestoes(textoCompleto);
            }

            // 4. Montar resposta
            Map<String, Object> response = new HashMap<>();
            response.put("textoExtraido", extraction.getText());
            response.put("textoCompleto", textoCompleto);
            response.put("totalPaginas", extraction.getSavedFiles().size());
            response.put("questoes", questoes);
            response.put("totalQuestoes", questoes != null ? questoes.size() : 0);
            response.put("imageOcr", extraction.getImageOcr());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "erro", "Falha ao processar arquivo",
                "mensagem", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para reprocessar texto já extraído
     * POST /extract/reparse
     */
    @PostMapping("/reparse")
    public ResponseEntity<Map<String, Object>> reparseText(@RequestBody Map<String, String> request) {
        String texto = request.get("texto");

        if (texto == null || texto.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "erro", "Campo 'texto' é obrigatório"
            ));
        }

        List<QuestaoExtraidaDTO> questoes = questionParserService.parseQuestoes(texto);

        Map<String, Object> response = new HashMap<>();
        response.put("questoes", questoes);
        response.put("totalQuestoes", questoes.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para validar qualidade do OCR
     * POST /extract/validate-ocr
     */
    @PostMapping("/validate-ocr")
    public ResponseEntity<Map<String, Object>> validateOcr(
            @RequestParam("file") MultipartFile file) {

        try {
            ExtractionResult extraction = fileExtractionService.extract(file);

            // Métricas de qualidade
            Map<String, Object> metricas = new HashMap<>();
            metricas.put("totalCaracteres", extraction.getText().length());
            metricas.put("totalPaginas", extraction.getSavedFiles().size());
            metricas.put("paginasComOcr", extraction.getImageOcr().values().stream()
                .filter(s -> !s.isBlank())
                .count());
            metricas.put("paginasVazias", extraction.getImageOcr().values().stream()
                .filter(String::isBlank)
                .count());

            // Calcular taxa de caracteres especiais (indicador de problemas de OCR)
            long caracteresEspeciais = extraction.getText().chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && c != '.' && c != ',' && c != '!' && c != '?')
                .count();
            double taxaEspeciais = (caracteresEspeciais * 100.0) / Math.max(1, extraction.getText().length());
            metricas.put("taxaCaracteresEspeciais", String.format("%.2f%%", taxaEspeciais));

            // Qualidade estimada
            String qualidade = "BOA";
            if (taxaEspeciais > 10) qualidade = "RUIM";
            else if (taxaEspeciais > 5) qualidade = "REGULAR";

            metricas.put("qualidadeEstimada", qualidade);

            Map<String, Object> response = new HashMap<>();
            response.put("metricas", metricas);
            response.put("textoExtraido", extraction.getText().substring(0, Math.min(500, extraction.getText().length())) + "...");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "erro", "Falha ao validar OCR",
                "mensagem", e.getMessage()
            ));
        }
    }

    /**
     * Combina texto principal com OCR das imagens
     */
    private String combinarTextos(ExtractionResult extraction) {
        StringBuilder combined = new StringBuilder();

        // Texto principal
        if (extraction.getText() != null && !extraction.getText().isBlank()) {
            combined.append(extraction.getText()).append("\n\n");
        }

        // Adicionar OCR de cada página (ordenado por nome de arquivo)
        extraction.getImageOcr().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (!entry.getValue().isBlank()) {
                    combined.append("--- ").append(entry.getKey()).append(" ---\n");
                    combined.append(entry.getValue()).append("\n\n");
                }
            });

        return combined.toString().trim();
    }
}

