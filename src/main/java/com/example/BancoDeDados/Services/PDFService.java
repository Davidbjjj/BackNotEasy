package com.example.BancoDeDados.Services;

import lombok.Data;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
@Data
public class PDFService {
    private String textoExtraido;

    public String extrairTextoPDF(String caminhoArquivo) throws IOException {
        File arquivoPDF = new File(caminhoArquivo);

        if (!arquivoPDF.exists()) {
            throw new IOException("Arquivo não encontrado: " + arquivoPDF.getAbsolutePath());
        }

        PDDocument documento =PDDocument.load(arquivoPDF);
        PDFTextStripper textType = new PDFTextStripper();
        String texto = textType.getText(documento);
        documento.close();
        return texto;
    }
    public String TextoExtraido() throws IOException {
        textoExtraido =extrairTextoPDF("C:\\Users\\David\\Downloads\\BancoDeDados\\BancoDeDados\\src\\main\\resources\\Arquivos\\teste.pdf");
   return textoExtraido;
    }

}
