package com.example.BancoDeDados.Services;

import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@Data
public class PDFIAService {
    private String textoExtraido;

    public String extrairTextoPDF(MultipartFile arquivoPDF) throws IOException {
            try (PDDocument documento = PDDocument.load(arquivoPDF.getInputStream())) {
            PDFTextStripper textStripper = new PDFTextStripper();
            return textStripper.getText(documento);
        }
    }

    public String TextoExtraido(MultipartFile arquivoPDF) throws IOException {
        textoExtraido = extrairTextoPDF(arquivoPDF);
        return textoExtraido;
    }
}
