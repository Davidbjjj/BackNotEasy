package com.example.BancoDeDados.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lê a "Máscara de Correção" de cadernos de atividades: uma grade de bolinhas (linhas = questões,
 * colunas = A..E) em que a resposta certa vem preenchida de preto. Nenhum OCR de texto lê isso,
 * então a imagem é analisada por pixels: acha as bolinhas pretas e converte posição em letra.
 */
@Service
public class GabaritoMascaraService {

    private static final Logger logger = LoggerFactory.getLogger(GabaritoMascaraService.class);

    /** Linhas da grade (questões por cartão). */
    public static final int LINHAS = 10;
    /** Posição horizontal (fração da largura) do centro de cada coluna A..E no modelo do cartão. */
    private static final double[] COLUNAS = {0.245, 0.41, 0.59, 0.76, 0.93};
    private static final int LIMITE_PIXELS = 4_000_000;
    private static final int ESCURO = 70; // luminosidade (0-255) abaixo da qual o pixel conta como "preenchido"

    /**
     * @return para cada questão (0..9) o índice da alternativa marcada (0=A) ou -1; null se a imagem
     *         não for uma máscara de correção.
     */
    public int[] ler(File arquivo) {
        try {
            BufferedImage bi = ImageIO.read(arquivo);
            return bi == null ? null : ler(bi);
        } catch (Exception e) {
            logger.debug("Não foi possível analisar {} como máscara: {}", arquivo.getName(), e.getMessage());
            return null;
        }
    }

    int[] ler(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        double proporcao = (double) h / w;
        // O cartão é uma imagem "em pé" de tamanho razoável; descarta ícones, faixas e páginas inteiras
        if (w < 250 || w > 2500 || proporcao < 1.1 || proporcao > 1.6 || (long) w * h > LIMITE_PIXELS) {
            return null;
        }

        boolean[] escuro = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = bi.getRGB(x, y);
                int lum = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                escuro[y * w + x] = ((rgb >>> 24) & 0xFF) > 128 && lum < ESCURO;
            }
        }

        int[] marcadas = new int[LINHAS];
        Arrays.fill(marcadas, -1);
        int[] contagemPorLinha = new int[LINHAS];
        int bolinhas = 0;

        boolean[] visto = new boolean[w * h];
        double areaMin = 0.003 * w * h, areaMax = 0.03 * w * h;
        for (int inicio = 0; inicio < escuro.length; inicio++) {
            if (!escuro[inicio] || visto[inicio]) continue;
            Componente c = preencher(escuro, visto, w, h, inicio);
            double quadrado = (double) c.largura() / c.altura();
            double preenchimento = (double) c.area / ((double) c.largura() * c.altura());
            if (c.area < areaMin || c.area > areaMax || quadrado < 0.75 || quadrado > 1.33 || preenchimento < 0.6) {
                continue; // texto, linhas de grade, bordas: não é bolinha preenchida
            }
            double cx = (c.minX + c.maxX) / 2.0 / w;
            double cy = (c.minY + c.maxY) / 2.0 / h;
            int coluna = colunaMaisProxima(cx);
            int linha = (int) (cy * LINHAS);
            if (coluna < 0 || linha < 0 || linha >= LINHAS) continue;
            marcadas[linha] = coluna;
            contagemPorLinha[linha]++;
            bolinhas++;
        }

        // Duas marcas na mesma linha = resposta ambígua
        for (int i = 0; i < LINHAS; i++) {
            if (contagemPorLinha[i] > 1) marcadas[i] = -1;
        }
        if (bolinhas < 3) {
            return null; // pouca coisa para ser um cartão preenchido
        }
        logger.info("Máscara de correção detectada ({}x{}): {}", w, h, formatar(marcadas));
        return marcadas;
    }

    /** A letra (a..e) de cada questão, ou "" quando não marcada. */
    public static String formatar(int[] marcadas) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < marcadas.length; i++) {
            sb.append(i + 1).append(marcadas[i] < 0 ? "-" : String.valueOf((char) ('A' + marcadas[i]))).append(' ');
        }
        return sb.toString().trim();
    }

    private static int colunaMaisProxima(double cx) {
        int melhor = -1;
        double menor = 0.07; // tolerância: ~7% da largura
        for (int i = 0; i < COLUNAS.length; i++) {
            double d = Math.abs(cx - COLUNAS[i]);
            if (d < menor) {
                menor = d;
                melhor = i;
            }
        }
        return melhor;
    }

    private record Componente(int area, int minX, int minY, int maxX, int maxY) {
        int largura() { return maxX - minX + 1; }
        int altura() { return maxY - minY + 1; }
    }

    /** Preenchimento por inundação (4 vizinhos) sem recursão. */
    private static Componente preencher(boolean[] escuro, boolean[] visto, int w, int h, int inicio) {
        ArrayDeque<Integer> fila = new ArrayDeque<>();
        fila.add(inicio);
        visto[inicio] = true;
        int area = 0, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
        while (!fila.isEmpty()) {
            int p = fila.poll();
            int x = p % w, y = p / w;
            area++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            if (x > 0) visitar(escuro, visto, fila, p - 1);
            if (x < w - 1) visitar(escuro, visto, fila, p + 1);
            if (y > 0) visitar(escuro, visto, fila, p - w);
            if (y < h - 1) visitar(escuro, visto, fila, p + w);
        }
        return new Componente(area, minX, minY, maxX, maxY);
    }

    private static void visitar(boolean[] escuro, boolean[] visto, ArrayDeque<Integer> fila, int p) {
        if (escuro[p] && !visto[p]) {
            visto[p] = true;
            fila.add(p);
        }
    }

    /** Conveniência para testes/uso em lote: lê várias imagens e devolve só as que são máscaras. */
    public List<int[]> lerTodas(List<File> arquivos) {
        List<int[]> saida = new ArrayList<>();
        for (File f : arquivos) {
            int[] m = ler(f);
            if (m != null) saida.add(m);
        }
        return saida;
    }
}
