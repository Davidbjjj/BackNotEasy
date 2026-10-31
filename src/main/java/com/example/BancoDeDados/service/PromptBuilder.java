package com.example.BancoDeDados.service;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PromptBuilder {

    public static class ImageOcrResult {
        public final String filename;
        public final String ocrText;
        public final String mathLatex;
        public final String caption;

        public ImageOcrResult(String filename, String ocrText, String mathLatex, String caption) {
            this.filename = filename;
            this.ocrText = ocrText == null ? "" : ocrText.trim();
            this.mathLatex = mathLatex == null ? "" : mathLatex.trim();
            this.caption = caption == null ? "" : caption.trim();
        }

        public String toAnnotatedBlock(int index) {
            List<String> parts = new ArrayList<>();
            parts.add(String.format("[IMAGE_%d: %s]", index, filename));
            if (!ocrText.isEmpty()) parts.add("OCR_TEXT: " + ocrText);
            if (!mathLatex.isEmpty()) parts.add("MATH_LATEX: " + mathLatex);
            if (!caption.isEmpty()) parts.add("CAPTION: " + caption);
            if (parts.size() == 1) parts.add("CAPTION: (no text found, visual content only)");
            return String.join(System.lineSeparator(), parts);
        }
    }

    public static String buildPrompt(String extractionText, Map<String, ImageOcrResult> imageResults) {
        if (extractionText == null) extractionText = "";

        String working = extractionText;
        List<String> appended = new ArrayList<>();
        int idx = 1;

        for (ImageOcrResult r : imageResults.values()) {
            String simpleName = Path.of(r.filename).getFileName().toString();
            String annotated = r.toAnnotatedBlock(idx++);
            if (working.contains(simpleName)) {
                working = working.replace(simpleName, annotated);
            } else if (working.contains(r.filename)) {
                working = working.replace(r.filename, annotated);
            } else {
                appended.add(annotated);
            }
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Extracted text:").append(System.lineSeparator());
        prompt.append(working.trim()).append(System.lineSeparator()).append(System.lineSeparator());

        if (!appended.isEmpty()) {
            prompt.append("Embedded images / snapshots:").append(System.lineSeparator());
            prompt.append(appended.stream().collect(Collectors.joining(System.lineSeparator() + System.lineSeparator())));
            prompt.append(System.lineSeparator());
        }

        prompt.append(System.lineSeparator());
        prompt.append("Instruction: When answering or extracting questions, use the OCR_TEXT, MATH_LATEX or CAPTION blocks above for any visual content that cannot be represented as plain text.").append(System.lineSeparator());

        return prompt.toString();
    }
}

