package com.example.BancoDeDados.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Serviço para extrair imagens embutidas de PDFs
 * Diferente do approach de renderizar páginas inteiras, este extrai as imagens originais
 */
@Service
public class PDFImageExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(PDFImageExtractorService.class);

    /**
     * Extrai todas as imagens embutidas de um PDF
     *
     * @param pdfFile Arquivo PDF
     * @param outputDir Diretório onde salvar as imagens extraídas
     * @param prefix Prefixo para nomes dos arquivos
     * @return Lista de caminhos das imagens extraídas
     */
    public List<String> extractImagesFromPDF(File pdfFile, Path outputDir, String prefix) throws IOException {
        List<String> extractedImages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int imageCounter = 0;

            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage page = document.getPage(pageNum);
                PDResources resources = page.getResources();

                // Extrair imagens XObject da página
                Iterable<org.apache.pdfbox.cos.COSName> xObjectNames = resources.getXObjectNames();

                for (org.apache.pdfbox.cos.COSName name : xObjectNames) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.PDXObject xObject = resources.getXObject(name);

                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject image = (PDImageXObject) xObject;

                            int width = image.getWidth();
                            int height = image.getHeight();

                            // Filtrar imagens muito pequenas (ícones/logos) ou muito grandes (páginas escaneadas)
                            if (width < 50 || height < 50) {
                                logger.debug("Ignorando imagem pequena {}x{} (ícone/logo)", width, height);
                                continue;
                            }

                            // NOVO: Ignorar imagens que parecem ser páginas inteiras escaneadas
                            // Páginas A4 em 150-300 DPI têm ~1500x2100 pixels ou mais
                            if (width > 1000 && height > 1400) {
                                logger.debug("Ignorando imagem grande {}x{} (parece página inteira escaneada)", width, height);
                                continue;
                            }

                            // NOVO: Filtrar por aspect ratio (páginas têm ratio ~1.4, expressões são mais variadas)
                            double aspectRatio = (double) height / width;
                            if (aspectRatio > 1.3 && aspectRatio < 1.5 && width > 800) {
                                logger.debug("Ignorando imagem {}x{} (aspect ratio {} parece página A4)",
                                           width, height, String.format("%.2f", aspectRatio));
                                continue;
                            }

                            // Salvar imagem (expressão matemática, gráfico, diagrama)
                            String filename = String.format("%s-page%d-img%d.png",
                                                          prefix != null ? prefix : "image",
                                                          pageNum + 1,
                                                          imageCounter);
                            File outputFile = outputDir.resolve(filename).toFile();

                            BufferedImage bufferedImage = image.getImage();
                            ImageIO.write(bufferedImage, "PNG", outputFile);

                            extractedImages.add(outputFile.getAbsolutePath());
                            imageCounter++;

                            logger.info("Extraída imagem (expressão/gráfico) {}x{} da página {} -> {}",
                                       width, height, pageNum + 1, filename);
                        }
                    } catch (Exception e) {
                        logger.warn("Erro ao extrair imagem da página {}: {}", pageNum + 1, e.getMessage());
                    }
                }

                // Se não encontrou imagens embutidas, fazer fallback para renderizar a página
                if (imageCounter == 0 && pageNum == 0) {
                    logger.warn("Nenhuma imagem embutida encontrada na página 1, usando fallback de renderização");
                    String fallbackImage = renderPageWithImageDetection(renderer, pageNum, outputDir, prefix);
                    if (fallbackImage != null) {
                        extractedImages.add(fallbackImage);
                    }
                }
            }

            // Se não encontrou NENHUMA imagem, fazer fallback completo
            if (extractedImages.isEmpty()) {
                logger.warn("Nenhuma imagem embutida encontrada no PDF, renderizando páginas com detecção de conteúdo");
                extractedImages = renderPagesWithContentDetection(document, renderer, outputDir, prefix);
            }

            logger.info("Total de imagens extraídas: {}", extractedImages.size());
        }

        return extractedImages;
    }

    /**
     * Renderiza página e tenta detectar regiões com imagens/gráficos
     */
    private String renderPageWithImageDetection(PDFRenderer renderer, int pageNum,
                                                Path outputDir, String prefix) {
        try {
            BufferedImage fullPage = renderer.renderImageWithDPI(pageNum, 200);

            // Detectar regiões com conteúdo visual (não-branco)
            Rectangle contentBounds = detectContentBounds(fullPage);

            if (contentBounds != null) {
                // Recortar apenas a região com conteúdo
                BufferedImage croppedImage = fullPage.getSubimage(
                    contentBounds.x,
                    contentBounds.y,
                    contentBounds.width,
                    contentBounds.height
                );

                String filename = String.format("%s-page%d-detected.png",
                                              prefix != null ? prefix : "image",
                                              pageNum + 1);
                File outputFile = outputDir.resolve(filename).toFile();
                ImageIO.write(croppedImage, "PNG", outputFile);

                logger.info("Renderizada página {} com detecção de conteúdo: {}x{}",
                           pageNum + 1, contentBounds.width, contentBounds.height);

                return outputFile.getAbsolutePath();
            }
        } catch (Exception e) {
            logger.error("Erro ao renderizar página {}: {}", pageNum + 1, e.getMessage());
        }

        return null;
    }

    /**
     * Renderiza todas as páginas com detecção de conteúdo (fallback final)
     */
    private List<String> renderPagesWithContentDetection(PDDocument document, PDFRenderer renderer,
                                                         Path outputDir, String prefix) {
        List<String> images = new ArrayList<>();

        for (int i = 0; i < document.getNumberOfPages(); i++) {
            String image = renderPageWithImageDetection(renderer, i, outputDir, prefix);
            if (image != null) {
                images.add(image);
            }
        }

        return images;
    }

    /**
     * Detecta os limites do conteúdo não-branco em uma imagem
     * Útil para recortar margens brancas
     */
    private Rectangle detectContentBounds(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = width;
        int minY = height;
        int maxX = 0;
        int maxY = 0;

        boolean foundContent = false;

        // Threshold para considerar "não-branco"
        int whiteThreshold = 240; // RGB > 240 é considerado branco

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Se não é branco
                if (r < whiteThreshold || g < whiteThreshold || b < whiteThreshold) {
                    foundContent = true;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (!foundContent) {
            return null;
        }

        // Adicionar margem de 10 pixels
        int margin = 10;
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);

        int contentWidth = maxX - minX + 1;
        int contentHeight = maxY - minY + 1;

        // Validar que o conteúdo tem tamanho razoável
        if (contentWidth < 50 || contentHeight < 50) {
            return null;
        }

        return new Rectangle(minX, minY, contentWidth, contentHeight);
    }

    /**
     * Versão simplificada: extrai apenas imagens XObject (mais rápido)
     */
    public List<String> extractEmbeddedImagesOnly(File pdfFile, Path outputDir, String prefix) throws IOException {
        List<String> extractedImages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            int imageCounter = 0;

            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage page = document.getPage(pageNum);
                PDResources resources = page.getResources();

                for (org.apache.pdfbox.cos.COSName name : resources.getXObjectNames()) {
                    try {
                        org.apache.pdfbox.pdmodel.graphics.PDXObject xObject = resources.getXObject(name);

                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject image = (PDImageXObject) xObject;

                            // Filtrar imagens pequenas
                            if (image.getWidth() >= 100 && image.getHeight() >= 100) {
                                String filename = String.format("%s-img%d.png",
                                                              prefix != null ? prefix : "image",
                                                              imageCounter);
                                File outputFile = outputDir.resolve(filename).toFile();

                                ImageIO.write(image.getImage(), "PNG", outputFile);
                                extractedImages.add(outputFile.getAbsolutePath());
                                imageCounter++;

                                logger.debug("Extraída imagem embutida: {}", filename);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Erro ao extrair imagem: {}", e.getMessage());
                    }
                }
            }
        }

        return extractedImages;
    }
}

