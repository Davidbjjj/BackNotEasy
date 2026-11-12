package com.example.BancoDeDados.Services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** FE-208: conteúdo dos arquivos gerados (CSV e PDF), sem depender do banco. */
class ExportacaoArquivosTest {

    private static Map<String, String> linha(String aluno, String nota) {
        return Map.of("aluno", aluno, "nota", nota, "lista", "Lista; 1");
    }

    @Test
    void csvTemBomCabecalhoEscolhidoEEscapaCampos() {
        String csv = new String(ExportacaoService.csv(List.of("aluno", "lista", "nota"),
                List.of(linha("Ana \"A\" Souza", "7,50"))), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿"));
        String[] linhas = csv.substring(1).split("\r\n");
        assertEquals("Aluno;Lista;Nota", linhas[0]);
        assertEquals("\"Ana \"\"A\"\" Souza\";\"Lista; 1\";7,50", linhas[1]);
    }

    @Test
    void csvNeutralizaFormulasMasMantemNumeros() {
        String csv = new String(ExportacaoService.csv(List.of("aluno", "nota"),
                List.of(Map.of("aluno", "=HYPERLINK(\"http://x\")", "nota", "-1,5"))), StandardCharsets.UTF_8);
        String linha = csv.substring(1).split("\r\n")[1];
        assertTrue(linha.startsWith("\"'=HYPERLINK") || linha.startsWith("'=HYPERLINK"), linha);
        assertTrue(linha.endsWith(";-1,5"), linha);
    }

    @Test
    void pdfTemUmaPaginaOuMaisESuportaAcentosEListasLongas() throws Exception {
        List<Map<String, String>> muitas = new ArrayList<>();
        for (int i = 0; i < 120; i++) muitas.add(linha("Aluno Ç " + i, "9,0"));
        byte[] pdf = ExportacaoService.pdf("Português — 1º semestre", List.of("aluno", "lista", "nota"), muitas);
        assertTrue(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1).startsWith("%PDF"));
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 3, "120 linhas devem ocupar várias páginas");
        }
        byte[] vazio = ExportacaoService.pdf("Sem alunos", List.of("aluno"), List.of());
        try (PDDocument doc = PDDocument.load(vazio)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }
}
