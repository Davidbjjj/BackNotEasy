package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Services.ExtratorService;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;

@RestController
@RequestMapping("/ler")
public class ExtratorController {

    @Autowired
    private ExtratorService extratorService;

    @PostMapping
    public ResponseEntity<String> extrair(@RequestPart("file") MultipartFile file) {
        try {
            File tempFile = File.createTempFile("ocr_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);

            String textoExtraido = extratorService.extrair(tempFile.getAbsolutePath());

            tempFile.delete();

            return ResponseEntity.ok(textoExtraido);

        } catch (IOException | TesseractException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao processar OCR: " + e.getMessage());
        }
    }
}
