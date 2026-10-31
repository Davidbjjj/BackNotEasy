package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.service.ExtractionResult;
import com.example.BancoDeDados.service.FileExtractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/extract")
public class ExtractionController {

    private final FileExtractionService fileExtractionService;

    public ExtractionController(FileExtractionService fileExtractionService) {
        this.fileExtractionService = fileExtractionService;
    }

    @PostMapping("/file")
    public ResponseEntity<ExtractionResult> extractFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePrefix", required = false) String filePrefix) {
        try {
            ExtractionResult res = fileExtractionService.extract(file, filePrefix);
            return ResponseEntity.ok(res);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }
}
