package com.example.BancoDeDados.Services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** Geração genérica de CSV e PDF de tabelas (relatórios da instituição, FE-305 e FE-308). */
public final class ArquivosUtil {

    private ArquivosUtil() {}

    /** CSV com BOM e ponto e vírgula; neutraliza células que começam com = + - @ (injeção de fórmula). */
    public static byte[] csv(List<String> cabecalho, List<List<String>> linhas) {
        StringBuilder sb = new StringBuilder("﻿");
        sb.append(cabecalho.stream().map(ArquivosUtil::campo).collect(Collectors.joining(";"))).append("\r\n");
        for (List<String> l : linhas) sb.append(l.stream().map(ArquivosUtil::campo).collect(Collectors.joining(";"))).append("\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String campo(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?[0-9]+([.,][0-9]+)?")) s = "'" + s;
        if (s.contains(";") || s.contains("\"") || s.contains("\n") || s.contains("\r")) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    /** Mantém só o que a fonte padrão do PDF (WinAnsi) desenha, e corta o excesso. */
    public static String limpar(String v, int max) {
        String s = v == null ? "" : v;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) sb.append(c >= 32 && c <= 255 && c != 127 ? c : '?');
        String t = sb.toString();
        return t.length() > max ? t.substring(0, Math.max(1, max - 1)) + "." : t;
    }

    /** Desenha o logo (PNG/JPG) no canto superior esquerdo; falha de imagem nunca derruba o documento. */
    public static float desenharLogo(PDDocument doc, PDPageContentStream cs, byte[] logo, float x, float yTopo, float altura) {
        if (logo == null || logo.length == 0) return 0;
        try {
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, logo, "logo");
            float largura = img.getWidth() * (altura / img.getHeight());
            cs.drawImage(img, x, yTopo - altura, largura, altura);
            return largura;
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    public static void texto(PDPageContentStream cs, PDType1Font fonte, float tam, float x, float y, String t) throws IOException {
        cs.beginText();
        cs.setFont(fonte, tam);
        cs.newLineAtOffset(x, y);
        cs.showText(limpar(t, 200));
        cs.endText();
    }

    /** PDF paisagem com logo, título, subtítulos e uma tabela paginada. */
    public static byte[] tabelaPdf(String titulo, List<String> subtitulos, byte[] logo, List<String> cabecalho,
                                   List<List<String>> linhas) throws IOException {
        PDRectangle pag = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
        float margem = 36;
        float colW = (pag.getWidth() - 2 * margem) / cabecalho.size();
        int maxChars = Math.max(6, (int) (colW / 4.2f));
        float alturaLinha = 14;
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPageContentStream cs = null;
            float y = 0;
            int idx = 0;
            do {
                if (cs == null || y < margem + alturaLinha) {
                    if (cs != null) cs.close();
                    PDPage page = new PDPage(pag);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    y = pag.getHeight() - margem;
                    float lw = desenharLogo(doc, cs, logo, margem, y, 34);
                    texto(cs, PDType1Font.HELVETICA_BOLD, 14, margem + (lw > 0 ? lw + 10 : 0), y - 14, titulo);
                    float yy = y - 28;
                    for (String s : subtitulos) {
                        texto(cs, PDType1Font.HELVETICA, 9, margem + (lw > 0 ? lw + 10 : 0), yy, s);
                        yy -= 11;
                    }
                    y = Math.min(y - 40, yy) - 8;
                    for (int c = 0; c < cabecalho.size(); c++) {
                        texto(cs, PDType1Font.HELVETICA_BOLD, 8, margem + c * colW, y, limpar(cabecalho.get(c), maxChars));
                    }
                    y -= alturaLinha;
                }
                if (idx < linhas.size()) {
                    List<String> l = linhas.get(idx);
                    for (int c = 0; c < cabecalho.size(); c++) {
                        texto(cs, PDType1Font.HELVETICA, 8, margem + c * colW, y, limpar(c < l.size() ? l.get(c) : "", maxChars));
                    }
                    y -= alturaLinha;
                }
                idx++;
            } while (idx < linhas.size());
            if (linhas.isEmpty()) texto(cs, PDType1Font.HELVETICA, 10, margem, y, "Nenhum registro no período.");
            cs.close();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
