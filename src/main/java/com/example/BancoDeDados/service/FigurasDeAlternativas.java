package com.example.BancoDeDados.service;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Figuras que são alternativas (gráficos A..E) e não chegam ao sistema como uma imagem por alternativa:
 * <ul>
 *   <li>{@link #dividirComposta}: uma única imagem com os cinco painéis (e os rótulos "A)", "B)"...) é
 *       cortada em cinco imagens, na ordem de leitura;</li>
 *   <li>{@link #molduraDeGraficosVetoriais}: gráficos desenhados em vetor (sem imagem no PDF), cada um dentro
 *       de uma moldura retangular, têm a moldura localizada para serem recortados da página renderizada.</li>
 * </ul>
 */
final class FigurasDeAlternativas {

    private FigurasDeAlternativas() {
    }

    // ------------------------------------------------------------------ imagem composta

    /** Um painel recortado e sua posição relativa dentro da imagem original (frações 0..1). */
    record Painel(BufferedImage imagem, double topo, double base, double esq, double dir) {
    }

    /**
     * Divide uma imagem em painéis separados por faixas brancas. Só devolve algo quando acha exatamente
     * cinco painéis de tamanho parecido (as cinco alternativas); do contrário, null e a imagem segue inteira.
     */
    static List<Painel> dividirComposta(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        if (w < 300 || h < 300 || w > 3000 || h > 3000) return null;

        boolean[][] tinta = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = bi.getRGB(x, y);
                int lum = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                tinta[y][x] = ((rgb >>> 24) & 0xFF) > 128 && lum < 235;
            }
        }

        // linhas de painéis: faixas de altura com tinta, separadas por pelo menos 6 px em branco
        boolean[] linhaComTinta = new boolean[h];
        for (int y = 0; y < h; y++) {
            int n = 0;
            for (int x = 0; x < w; x++) if (tinta[y][x]) n++;
            linhaComTinta[y] = n >= 2;
        }
        List<int[]> faixasY = faixas(linhaComTinta, 6, (int) (0.08 * h));

        List<Painel> paineis = new ArrayList<>();
        for (int[] fy : faixasY) {
            boolean[] colunaComTinta = new boolean[w];
            for (int x = 0; x < w; x++) {
                int n = 0;
                for (int y = fy[0]; y <= fy[1]; y++) if (tinta[y][x]) n++;
                colunaComTinta[x] = n >= 2;
            }
            List<int[]> faixasX = faixas(colunaComTinta, 6, 1);
            // faixa estreita (rótulo "A)") é colada ao painel seguinte
            List<int[]> fundidas = new ArrayList<>();
            for (int i = 0; i < faixasX.size(); i++) {
                int[] f = faixasX.get(i);
                if (f[1] - f[0] + 1 < 0.08 * w && i + 1 < faixasX.size()) {
                    faixasX.get(i + 1)[0] = f[0];
                } else {
                    fundidas.add(f);
                }
            }
            for (int[] fx : fundidas) {
                if (fx[1] - fx[0] + 1 < 0.15 * w) continue; // ruído
                int x0 = Math.max(0, fx[0] - 3), x1 = Math.min(w - 1, fx[1] + 3);
                int y0 = Math.max(0, fy[0] - 3), y1 = Math.min(h - 1, fy[1] + 3);
                paineis.add(new Painel(bi.getSubimage(x0, y0, x1 - x0 + 1, y1 - y0 + 1),
                        y0 / (double) h, (y1 + 1) / (double) h, x0 / (double) w, (x1 + 1) / (double) w));
            }
        }
        if (paineis.size() != 5) return null;

        // as cinco alternativas têm tamanho parecido
        List<Integer> larguras = new ArrayList<>(), alturas = new ArrayList<>();
        for (Painel p : paineis) {
            larguras.add(p.imagem().getWidth());
            alturas.add(p.imagem().getHeight());
        }
        larguras.sort(Comparator.naturalOrder());
        alturas.sort(Comparator.naturalOrder());
        int mw = larguras.get(2), mh = alturas.get(2);
        for (Painel p : paineis) {
            if (Math.abs(p.imagem().getWidth() - mw) > 0.3 * mw || Math.abs(p.imagem().getHeight() - mh) > 0.3 * mh) return null;
        }
        return paineis;
    }

    /** Trechos consecutivos "com tinta" [ini, fim], unindo vãos menores que {@code vaoMin}; descarta os menores que {@code tamMin}. */
    private static List<int[]> faixas(boolean[] comTinta, int vaoMin, int tamMin) {
        List<int[]> saida = new ArrayList<>();
        int ini = -1, ultima = -1;
        for (int i = 0; i < comTinta.length; i++) {
            if (comTinta[i]) {
                if (ini < 0) ini = i;
                else if (i - ultima > vaoMin) {
                    saida.add(new int[]{ini, ultima});
                    ini = i;
                }
                ultima = i;
            }
        }
        if (ini >= 0) saida.add(new int[]{ini, ultima});
        saida.removeIf(f -> f[1] - f[0] + 1 < tamMin);
        return saida;
    }

    // ------------------------------------------------------------------ gráficos em vetor

    /**
     * Molduras (em pontos, origem no canto superior esquerdo da página) de gráficos desenhados em vetor:
     * retângulos de tamanho de figura que contêm traçados (eixos, barras, curvas). Tabelas e caixas de texto
     * não têm traçados dentro e ficam de fora.
     */
    static List<Rectangle2D.Double> molduraDeGraficosVetoriais(PDPage page) {
        List<Rectangle2D.Double> saida = new ArrayList<>();
        if (page.getRotation() != 0) return saida;
        Coletor c = new Coletor(page);
        try {
            c.processPage(page);
        } catch (Exception e) {
            return saida;
        }
        double larguraPagina = page.getCropBox().getWidth();
        for (Rectangle2D.Double m : c.molduras.values()) {
            if (m.width < 100 || m.width > 420 || m.height < 60 || m.height > 340 || m.width > 0.8 * larguraPagina) continue;
            int dentro = 0;
            for (Rectangle2D.Double t : c.tracos) {
                if (t.x >= m.x - 2 && t.y >= m.y - 2 && t.x + t.width <= m.x + m.width + 2 && t.y + t.height <= m.y + m.height + 2) dentro++;
            }
            if (dentro >= 2) saida.add(m);
        }
        saida.sort(Comparator.comparingDouble((Rectangle2D.Double r) -> r.y).thenComparingDouble(r -> r.x));
        return saida;
    }

    private static final class Coletor extends PDFGraphicsStreamEngine {
        final Map<String, Rectangle2D.Double> molduras = new LinkedHashMap<>();
        final List<Rectangle2D.Double> tracos = new ArrayList<>();
        private final List<double[]> pontos = new ArrayList<>();
        private final List<Rectangle2D.Double> retangulos = new ArrayList<>();
        private final double altura, deslocX, deslocY;
        private double atualX, atualY;

        Coletor(PDPage page) {
            super(page);
            this.altura = page.getCropBox().getHeight();
            this.deslocX = page.getCropBox().getLowerLeftX();
            this.deslocY = page.getCropBox().getLowerLeftY();
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            double xa = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX())) - deslocX;
            double xb = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX())) - deslocX;
            double ya = altura - (Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY())) - deslocY);
            double yb = altura - (Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY())) - deslocY);
            retangulos.add(new Rectangle2D.Double(xa, ya, xb - xa, yb - ya));
        }

        @Override
        public void moveTo(float x, float y) {
            pontos.add(new double[]{x - deslocX, altura - (y - deslocY)});
            atualX = x;
            atualY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            moveTo(x, y);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            moveTo(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Double(atualX, atualY);
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
            limpar(); // recortes (clip) não são desenho
        }

        @Override
        public void strokePath() {
            pintar();
        }

        @Override
        public void fillPath(int windingRule) {
            pintar();
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            pintar();
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void drawImage(PDImage pdImage) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }

        private void pintar() {
            for (Rectangle2D.Double r : retangulos) {
                String chave = Math.round(r.x) + "|" + Math.round(r.y) + "|" + Math.round(r.width) + "|" + Math.round(r.height);
                molduras.putIfAbsent(chave, r);
            }
            if (pontos.size() >= 4) {
                double x1 = Double.MAX_VALUE, y1 = Double.MAX_VALUE, x2 = -Double.MAX_VALUE, y2 = -Double.MAX_VALUE;
                for (double[] p : pontos) {
                    x1 = Math.min(x1, p[0]);
                    y1 = Math.min(y1, p[1]);
                    x2 = Math.max(x2, p[0]);
                    y2 = Math.max(y2, p[1]);
                }
                tracos.add(new Rectangle2D.Double(x1, y1, x2 - x1, y2 - y1));
            }
            limpar();
        }

        private void limpar() {
            pontos.clear();
            retangulos.clear();
        }
    }
}
