package com.example.BancoDeDados.service;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.mime.MimeTypeException;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

public class TikaEmbeddedExtractor implements EmbeddedDocumentExtractor {
    private final Path outputDir;
    private final ExtractionResult result;
    private final String filePrefix;
    private final AtomicInteger counter = new AtomicInteger(0);

    public TikaEmbeddedExtractor(Path outputDir, ExtractionResult result, String filePrefix) {
        this.outputDir = outputDir;
        this.result = result;
        this.filePrefix = filePrefix != null && !filePrefix.isBlank() ? filePrefix : "embedded";
    }

    public TikaEmbeddedExtractor(Path outputDir, ExtractionResult result) {
        this(outputDir, result, "embedded");
    }

    @Override
    public boolean shouldParseEmbedded(Metadata metadata) {
        String ct = metadata.get(Metadata.CONTENT_TYPE);
        return ct == null || ct.startsWith("image") || ct.contains("xml") || ct.contains("math");
    }

    @Override
    public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata, boolean outputHtml) throws IOException {
        String name = metadata.get("resourceName");
        String contentType = metadata.get(Metadata.CONTENT_TYPE);

        // 1) Extrair só o basename se houver caminho
        if (name != null && !name.isBlank()) {
            name = Paths.get(name).getFileName().toString();
            // Sanitizar: remover caracteres perigosos
            name = name.replaceAll("[\\\\/]+", "_").replaceAll("[^A-Za-z0-9._-]", "_");
        }

        // 2) Se não houver nome válido, gerar um baseado no prefixo
        if (name == null || name.isBlank()) {
            name = filePrefix + "-" + System.currentTimeMillis() + "-" + counter.incrementAndGet();
        }

        // 3) Inferir extensão a partir do content-type
        String extension = inferExtension(contentType);
        if (!name.toLowerCase().endsWith(extension.toLowerCase())) {
            name = name + extension;
        }

        // 4) Prevenir colisão de arquivos
        Path out = outputDir.resolve(name);
        int collision = 1;
        while (Files.exists(out)) {
            String baseName = name.substring(0, name.lastIndexOf('.') > 0 ? name.lastIndexOf('.') : name.length());
            String newName = baseName + "-" + collision + extension;
            out = outputDir.resolve(newName);
            collision++;
        }

        // 5) Salvar o arquivo
        try (InputStream in = stream) {
            Files.copy(in, out);
        }
        result.addSavedFile(out.toAbsolutePath().toString());
    }

    private String inferExtension(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return ".bin";
        }
        try {
            MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
            MimeType mimeType = mimeTypes.forName(contentType);
            return mimeType.getExtension();
        } catch (MimeTypeException e) {
            // Fallback manual para tipos comuns
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("jpeg") || contentType.contains("jpg")) return ".jpg";
            if (contentType.contains("gif")) return ".gif";
            if (contentType.contains("pdf")) return ".pdf";
            if (contentType.contains("xml")) return ".xml";
            return ".bin";
        }
    }
}
