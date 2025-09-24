package com.example.BancoDeDados.Services;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
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

    public String extrair(String imagePath) throws IOException, TesseractException {
        File imageFile = new File(imagePath);

        if (!imageFile.exists()) {
            throw new IOException("Arquivo não encontrado: " + imagePath);
        }

        BufferedImage image = ImageIO.read(imageFile);

        if (image == null) {
            throw new IOException("Falha ao carregar imagem (formato não suportado): " + imagePath);
        }
        
        String resultado = tesseract.doOCR(image);

        return resultado != null ? resultado.trim() : "";
    }
}
