package com.example.BancoDeDados.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtractionResult {
    private String text;
    private List<String> savedFiles = new ArrayList<>();
    private Map<String, String> imageOcr = new HashMap<>();
    // Posição de cada imagem na página, indexada pelo nome do arquivo (só para PDF)
    private Map<String, PosicaoImagem> imagePositions = new HashMap<>();
    // Linhas de texto do PDF com posição, usadas para saber onde cada questão começa
    private List<LinhaPdf> pdfLines = new ArrayList<>();

    // Máscaras de correção (gabarito em bolinhas) lidas das imagens, na ordem do documento.
    // Cada uma tem, por questão, o índice da alternativa marcada (0=A) ou -1.
    private List<int[]> mascarasGabarito = new ArrayList<>();

    public ExtractionResult() {}

    public List<int[]> getMascarasGabarito() {
        return mascarasGabarito;
    }

    public void addMascaraGabarito(int[] mascara) {
        this.mascarasGabarito.add(mascara);
    }

    public Map<String, PosicaoImagem> getImagePositions() {
        return imagePositions;
    }

    public void addImagePosition(String filename, PosicaoImagem posicao) {
        this.imagePositions.put(filename, posicao);
    }

    public List<LinhaPdf> getPdfLines() {
        return pdfLines;
    }

    public void setPdfLines(List<LinhaPdf> pdfLines) {
        this.pdfLines = pdfLines;
    }

    public ExtractionResult(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getSavedFiles() {
        return savedFiles;
    }

    public void setSavedFiles(List<String> savedFiles) {
        this.savedFiles = savedFiles;
    }

    public void addSavedFile(String path) {
        this.savedFiles.add(path);
    }

    public Map<String, String> getImageOcr() {
        return imageOcr;
    }

    public void setImageOcr(Map<String, String> imageOcr) {
        this.imageOcr = imageOcr;
    }

    public void addImageOcr(String filename, String ocrText) {
        this.imageOcr.put(filename, ocrText);
    }
}

