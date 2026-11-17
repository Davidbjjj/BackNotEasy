package com.example.BancoDeDados.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class FileExtractionService {

    private final Path tmpRoot;
    private final OcrService ocrService;
    private final ParallelOcrService parallelOcrService;
    private final TextCleanerService textCleaner;
    private final PDFImageExtractorService pdfImageExtractor;
    private final GabaritoMascaraService gabaritoMascara;

    public FileExtractionService(OcrService ocrService,
                                 ParallelOcrService parallelOcrService,
                                 TextCleanerService textCleaner,
                                 PDFImageExtractorService pdfImageExtractor,
                                 GabaritoMascaraService gabaritoMascara) throws IOException {
        this.gabaritoMascara = gabaritoMascara;
        this.tmpRoot = Files.createTempDirectory("extractor-");
        this.ocrService = ocrService;
        this.parallelOcrService = parallelOcrService;
        this.textCleaner = textCleaner;
        this.pdfImageExtractor = pdfImageExtractor;
    }

    public ExtractionResult extract(MultipartFile file) throws IOException {
        return extract(file, null);
    }

    public ExtractionResult extract(MultipartFile file, String filePrefix) throws IOException {
        ExtractionResult result = new ExtractionResult();
        Path jobDir = tmpRoot.resolve(UUID.randomUUID().toString());
        Files.createDirectories(jobDir);

        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        String prefix = (filePrefix != null && !filePrefix.isBlank()) ? filePrefix : "embedded";

        // Verificar se é PDF
        boolean isPdf = filename.toLowerCase().endsWith(".pdf");

        if (isPdf) {
            // Para PDF, extrair diretamente as imagens sem usar Tika
            Path pdfCopy = jobDir.resolve("orig.pdf");
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, pdfCopy);
            }

            // Extrair texto usando PDFBox diretamente
            try {
                String textoExtraido = extractTextFromPDF(pdfCopy);
                String textoLimpo = textCleaner.fullClean(textoExtraido);
                result.setText(textoLimpo);
            } catch (Exception e) {
                System.err.println("Erro ao extrair texto do PDF: " + e.getMessage());
                result.setText(""); // Continua mesmo sem texto
            }

            // Extrair imagens do PDF
            renderPdfPages(pdfCopy, jobDir, result);

        } else {
            // Para outros formatos, usar Tika
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            context.set(org.apache.tika.extractor.EmbeddedDocumentExtractor.class,
                       new TikaEmbeddedExtractor(jobDir, result, prefix));

            try (InputStream in = file.getInputStream()) {
                parser.parse(in, handler, metadata, context);
                String textoExtraido = handler.toString();
                String textoLimpo = textCleaner.fullClean(textoExtraido);
                result.setText(textoLimpo);
            } catch (TikaException | SAXException e) {
                throw new IOException("Tika parsing failed", e);
            }
        }

        // Máscara de correção (gabarito em bolinhas): lê a marcação e tira a imagem da lista, para que
        // não seja anexada a uma questão como se fosse figura dela nem passe pelo OCR
        separarMascarasDeGabarito(result);

        // Run OCR on saved images (paralelo para melhor performance)
        if (!ocrService.isTesseractAvailable()) {
            System.out.println("AVISO: Tesseract OCR não está disponível. Imagens não serão processadas via OCR.");
        } else {
            // Processar OCR em paralelo se houver múltiplas imagens
            List<File> imageFiles = result.getSavedFiles().stream()
                .map(File::new)
                .collect(java.util.stream.Collectors.toList());

            if (imageFiles.size() > 1) {
                // Usar processamento paralelo
                java.util.concurrent.ConcurrentHashMap<String, String> ocrResults =
                    parallelOcrService.processarEmParalelo(imageFiles);
                ocrResults.forEach(result::addImageOcr);
            } else {
                // Uma única imagem - processar sequencialmente
                for (String pathStr : result.getSavedFiles()) {
                    File imgFile = new File(pathStr);
                    String ocr = ocrService.doOcr(imgFile);
                    result.addImageOcr(imgFile.getName(), ocr);
                }
            }
        }

        return result;
    }

    private void separarMascarasDeGabarito(ExtractionResult result) {
        List<String> restantes = new java.util.ArrayList<>();
        for (String caminho : result.getSavedFiles()) {
            File arquivo = new File(caminho);
            int[] mascara = gabaritoMascara.ler(arquivo);
            if (mascara == null) {
                restantes.add(caminho);
            } else {
                result.addMascaraGabarito(mascara);
                result.getImagePositions().remove(arquivo.getName());
            }
        }
        result.setSavedFiles(restantes);
    }

    private void renderPdfPages(Path pdfPath, Path outDir, ExtractionResult result) throws IOException {
        // Usar o novo extrator que pega apenas imagens embutidas
        List<PDFImageExtractorService.ImagemExtraida> extractedImages = pdfImageExtractor.extractImagesFromPDF(
            pdfPath.toFile(),
            outDir,
            "img"
        );

        // Adicionar ao resultado, guardando a posição de cada imagem na página
        for (PDFImageExtractorService.ImagemExtraida imagem : extractedImages) {
            result.addSavedFile(imagem.caminho());
            if (imagem.posicao() != null) {
                result.addImagePosition(new File(imagem.caminho()).getName(), imagem.posicao());
            }
        }

        // Linhas com posição: permitem saber onde cada questão começa e terminar de associar as imagens
        try {
            result.setPdfLines(pdfImageExtractor.extractLines(pdfPath.toFile()));
        } catch (Exception e) {
            System.err.println("Erro ao extrair posição das linhas do PDF: " + e.getMessage());
        }

        System.out.println("Extraídas " + extractedImages.size() + " imagens do PDF");
    }

    /**
     * Extrai texto de PDF usando PDFBox diretamente
     */
    private String extractTextFromPDF(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            // Ordem de leitura por posição/coluna, símbolos de equação e radical (ver PdfTextExtractor)
            return PdfTextExtractor.extrair(document);
        }
    }
}

