package com.example.BancoDeDados.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExtractionResult {
    private String text;
    private List<String> savedFiles = new ArrayList<>();
    private Map<String, String> imageOcr = new HashMap<>();

    public ExtractionResult() {}

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

