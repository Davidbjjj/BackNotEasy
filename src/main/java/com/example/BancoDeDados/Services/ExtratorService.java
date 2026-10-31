package com.example.BancoDeDados.Services;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Service
public class ExtratorService {

    private final ITesseract tesseract;

    public ExtratorService() {
        tesseract = new Tesseract();
        String dataPath = new File("src/main/resources/Arquivos").getAbsolutePath();
        tesseract.setDatapath(dataPath);
        tesseract.setLanguage("por");
    }

    public String extrair(String filePath) throws IOException, TesseractException {
        File file = new File(filePath);

        if (!file.exists()) {
            throw new IOException("Arquivo não encontrado: " + filePath);
        }


        if (filePath.toLowerCase().endsWith(".pdf")) {
            return extrairDePdf(file);
        } else {
            return extrairDeImagem(file);
        }
    }

    private String extrairDeImagem(File imageFile) throws IOException, TesseractException {
        BufferedImage image = ImageIO.read(imageFile);
        if (image == null) {
            throw new IOException("Falha ao carregar imagem: " + imageFile.getAbsolutePath());
        }
        return tesseract.doOCR(image).trim();
    }

    private String extrairDePdf(File pdfFile) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String textoExtraido = stripper.getText(document).trim();

            if (textoExtraido != null && !textoExtraido.isEmpty()) {
                return textoExtraido;
            } else {
                PDFRenderer renderer = new PDFRenderer(document);
                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 300);
                    String textoPagina = tesseract.doOCR(image);
                    sb.append(textoPagina).append("\n");
                }

                return sb.toString().trim();
            }
        }
    }
}