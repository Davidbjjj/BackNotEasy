package com.example.BancoDeDados.service;

import net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;

import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lê alternativas escritas numa imagem quando elas têm expoentes (ex.: o quadro "A) 1·10⁴ B) 0,1·10⁸ ...").
 * O OCR comum perde o expoente ("1·10⁷" vira "1107"), então a imagem é separada pela geometria:
 * <ol>
 *   <li>os componentes conexos (manchas de tinta) são agrupados nas cinco linhas pelos glifos de altura cheia;</li>
 *   <li>em cada linha, mancha menor que fica na metade de cima do corpo da linha é expoente;</li>
 *   <li>a linha é lida sem os expoentes, e cada grupo de expoente é lido sozinho, só com dígitos;</li>
 *   <li>o expoente entra no texto, em sobrescrito, logo depois do caractere à sua esquerda.</li>
 * </ol>
 * Conservador como o {@link AlternativasOcrParser}: qualquer leitura duvidosa devolve null e a imagem
 * continua anexada à questão.
 */
final class AlternativasComExpoentes {

    private static final int ESCALA = 3;
    /** Limiares de tinta (luminância) tentados em ordem: cada um separa melhor as manchas de um tipo de imagem. */
    private static final int[] LIMIARES = {200, 170, 185};
    private static final String SUP = "⁰¹²³⁴⁵⁶⁷⁸⁹";

    private AlternativasComExpoentes() {
    }

    private static final class Comp {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        final List<int[]> px = new ArrayList<>();
        int linha = -1;
        boolean expoente;

        void add(int x, int y, int rgb) {
            px.add(new int[]{x, y, rgb});
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
        }

        int h() {
            return y1 - y0 + 1;
        }

        double cy() {
            return (y0 + y1) / 2.0;
        }
    }

    private record Linha(double topo, double base) {
        double altura() {
            return base - topo;
        }
    }

    /**
     * @param tesseract instância já configurada (datapath/idioma); o modo de segmentação é alterado aqui
     * @return as cinco alternativas (sem o "A)"), na ordem; null se a leitura não for confiável
     */
    static List<String> ler(BufferedImage original, Tesseract tesseract) {
        if (original == null || original.getHeight() > 400 || original.getWidth() > 1200) return null;
        BufferedImage img = ampliar(original);
        for (int limiar : LIMIARES) {
            List<String> r = ler(img, limiar, tesseract);
            if (r != null) return r;
        }
        return null;
    }

    private static List<String> ler(BufferedImage img, int limiar, Tesseract tesseract) {
        int w = img.getWidth(), h = img.getHeight();
        boolean[][] tinta = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int lum = (int) (0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
                tinta[y][x] = lum < limiar;
            }
        }
        List<Comp> comps = componentes(tinta, img);
        comps.removeIf(c -> c.px.size() < 4 * ESCALA); // poeira
        if (comps.size() < 15) return null;

        // glifos de altura cheia: definem as linhas
        List<Integer> alturas = new ArrayList<>();
        for (Comp c : comps) alturas.add(c.h());
        alturas.sort(Comparator.reverseOrder());
        double alturaCheia = alturas.get(Math.min(alturas.size() - 1, alturas.size() / 4));
        List<Comp> grandes = new ArrayList<>();
        for (Comp c : comps) if (c.h() >= 0.6 * alturaCheia) grandes.add(c);
        grandes.sort(Comparator.comparingDouble(Comp::cy));

        List<List<Comp>> grupos = new ArrayList<>();
        for (Comp c : grandes) {
            List<Comp> ult = grupos.isEmpty() ? null : grupos.get(grupos.size() - 1);
            if (ult == null || c.cy() - ult.get(ult.size() - 1).cy() > 0.5 * alturaCheia) grupos.add(ult = new ArrayList<>());
            ult.add(c);
        }
        if (grupos.size() != 5) return null;

        List<Linha> linhas = new ArrayList<>();
        for (List<Comp> g : grupos) {
            linhas.add(new Linha(mediana(g.stream().map(c -> (double) c.y0).toList()),
                    mediana(g.stream().map(c -> (double) c.y1).toList())));
        }

        // cada mancha vai para a linha cujo corpo está mais perto; expoente = pequena e na metade de cima
        for (Comp c : comps) {
            double melhor = Double.MAX_VALUE;
            for (int i = 0; i < linhas.size(); i++) {
                Linha l = linhas.get(i);
                double d = c.cy() < l.topo() ? l.topo() - c.cy() : c.cy() > l.base() ? c.cy() - l.base() : 0;
                if (d < melhor) {
                    melhor = d;
                    c.linha = i;
                }
            }
            Linha l = linhas.get(c.linha);
            boolean pequena = c.h() < 0.85 * l.altura() && c.h() >= 0.3 * l.altura();
            // na metade de cima do corpo, ou passando bem acima do topo dele
            c.expoente = pequena && (c.y1 < l.topo() + 0.55 * l.altura() || c.y0 < l.topo() - 0.2 * l.altura());
        }
        // o vizinho colado à direita de um expoente, pequeno e alto, faz parte dele (o "2" de 10¹²)
        List<Comp> porX = new ArrayList<>(comps);
        porX.sort(Comparator.comparingInt(c -> c.x0));
        for (Comp e : porX) {
            if (!e.expoente) continue;
            Linha l = linhas.get(e.linha);
            for (Comp c : porX) {
                if (c.expoente || c.linha != e.linha || c.x0 < e.x1 || c.x0 - e.x1 > 0.3 * l.altura()) continue;
                if (c.h() < 0.85 * l.altura() && c.h() >= 0.3 * l.altura() && c.y1 < l.topo() + 0.7 * l.altura()) c.expoente = true;
            }
        }

        // sem expoente, o OCR comum (AlternativasOcrParser) já resolve
        if (comps.stream().noneMatch(c -> c.expoente)) return null;

        List<String> saida = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String alt = lerLinha(comps, i, linhas.get(i), w, img, tesseract);
            if (alt == null || alt.isBlank()) return null;
            saida.add(alt);
        }
        if (saida.stream().distinct().count() < 4) return null;
        return saida;
    }

    /** Lê o conteúdo de uma linha (sem o marcador "A)"), com expoentes e pontos de multiplicação no lugar. */
    private static String lerLinha(List<Comp> comps, int indice, Linha l, int largura, BufferedImage fonte, Tesseract tesseract) {
        double alt = l.altura();
        List<Comp> corpo = new ArrayList<>(), exps = new ArrayList<>();
        for (Comp c : comps) {
            if (c.linha != indice) continue;
            (c.expoente ? exps : corpo).add(c);
        }
        corpo.sort(Comparator.comparingInt(c -> c.x0));
        exps.sort(Comparator.comparingInt(c -> c.x0));
        if (corpo.size() < 3) return null;

        // marcador "A)": o primeiro bloco de manchas, até o primeiro espaço. A letra já é conhecida pela posição
        // da linha, e o itálico do marcador atrapalha o OCR do resto.
        int fimMarcador = 0;
        while (fimMarcador + 1 < corpo.size() && corpo.get(fimMarcador + 1).x0 - corpo.get(fimMarcador).x1 <= 0.3 * alt) {
            fimMarcador++;
        }
        Comp ultimoDoMarcador = corpo.get(fimMarcador);
        if (ultimoDoMarcador.x1 - corpo.get(0).x0 > 2.0 * alt || fimMarcador + 1 >= corpo.size()) return null;
        int inicioConteudo = ultimoDoMarcador.x1;
        corpo = new ArrayList<>(corpo.subList(fimMarcador + 1, corpo.size()));
        exps.removeIf(c -> c.x1 <= inicioConteudo);

        // insertos: textos que entram no OCR pela posição x (expoentes e pontos de multiplicação)
        List<Object[]> insertos = new ArrayList<>(); // {x, texto}

        // ponto de multiplicação "·": mancha pequena, redonda, na altura do meio do corpo
        for (java.util.Iterator<Comp> it = corpo.iterator(); it.hasNext(); ) {
            Comp c = it.next();
            int cw = c.x1 - c.x0 + 1;
            double meio = (c.cy() - l.topo()) / alt;
            if (c.h() <= 0.3 * alt && cw <= 0.3 * alt && cw >= 0.5 * c.h() && cw <= 2.0 * c.h() && meio > 0.3 && meio < 0.72) {
                insertos.add(new Object[]{(c.x0 + c.x1) / 2.0, " · "});
                it.remove();
            }
        }
        if (corpo.isEmpty()) return null;

        // grupos de expoente: manchas vizinhas na horizontal formam um número só (10¹¹)
        List<List<Comp>> gruposExp = new ArrayList<>();
        for (Comp c : exps) {
            List<Comp> ult = gruposExp.isEmpty() ? null : gruposExp.get(gruposExp.size() - 1);
            if (ult == null || c.x0 - ult.get(ult.size() - 1).x1 > 0.4 * alt) gruposExp.add(ult = new ArrayList<>());
            ult.add(c);
        }
        for (List<Comp> g : gruposExp) {
            String exp = lerExpoente(g, fonte, comps, tesseract);
            if (exp == null) return null;
            insertos.add(new Object[]{(double) g.get(0).x0, exp});
        }

        int margem = (int) Math.ceil(0.8 * alt);
        int x0 = Math.max(0, corpo.get(0).x0 - margem);
        int topo = (int) Math.max(0, l.topo() - margem), alturaImg = (int) (alt + 2 * margem);
        BufferedImage imgCorpo = pintar(corpo, x0, topo, largura - x0, alturaImg);

        // OCR livre; se a confiança for baixa, de novo só com caracteres de alternativa numérica
        Object[] melhor = null;
        for (String lista : new String[]{"", "0123456789,.+-=()/%$R "}) {
            Object[] r = ocrLinha(imgCorpo, lista, tesseract);
            if (r != null && (melhor == null || (float) r[2] > (float) melhor[2])) melhor = r;
            if (melhor != null && (float) melhor[2] >= 75) break;
        }
        tesseract.setVariable("tessedit_char_whitelist", "");
        if (melhor == null) return null;
        String texto = (String) melhor[0];
        @SuppressWarnings("unchecked")
        List<int[]> fimDoSimbolo = (List<int[]>) melhor[1];
        if ((float) melhor[2] < 60) return null;

        // cada inserto entra logo depois do último símbolo que termina à sua esquerda
        insertos.sort(Comparator.comparingDouble(o -> (double) o[0]));
        StringBuilder sb = new StringBuilder();
        int copiado = 0;
        for (Object[] ins : insertos) {
            double x = (double) ins[0] - x0;
            int pos = 0;
            for (int[] f : fimDoSimbolo) if (f[0] <= x + 0.15 * alt) pos = f[1];
            if (pos < copiado) return null;
            sb.append(texto, copiado, pos);
            if (" · ".equals(ins[1])) sb.append(" · ");
            else {
                while (sb.length() > 0 && sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
                sb.append((String) ins[1]);
            }
            copiado = pos;
        }
        sb.append(texto.substring(copiado));
        String r = sb.toString().replaceAll("\\s+", " ").strip();
        // potência de dez sem expoente: o expoente se perdeu, leitura incompleta
        if (r.matches(".*·\\s*10(?![⁰¹²³⁴⁵⁶⁷⁸⁹⁻\\d]).*")) return null;
        return r;
    }

    /** @return {texto, lista de {xFim do símbolo, posição no texto logo depois dele}, confiança}; null se nada foi lido */
    private static Object[] ocrLinha(BufferedImage img, String lista, Tesseract tesseract) {
        tesseract.setPageSegMode(7); // uma linha
        tesseract.setVariable("tessedit_char_whitelist", lista);
        List<Word> linhasOcr = tesseract.getWords(img, TessPageIteratorLevel.RIL_TEXTLINE);
        List<Word> simbolos = tesseract.getWords(img, TessPageIteratorLevel.RIL_SYMBOL);
        if (linhasOcr.isEmpty() || simbolos.isEmpty()) return null;
        String texto = linhasOcr.get(0).getText().strip();
        List<int[]> fimDoSimbolo = new ArrayList<>();
        int pos = 0;
        for (Word s : simbolos) {
            String t = s.getText().strip();
            if (t.isEmpty()) continue;
            int achado = texto.indexOf(t, pos);
            if (achado < 0) return null;
            pos = achado + t.length();
            fimDoSimbolo.add(new int[]{s.getBoundingBox().x + s.getBoundingBox().width, pos});
        }
        return new Object[]{texto, fimDoSimbolo, linhasOcr.get(0).getConfidence()};
    }

    private static String lerExpoente(List<Comp> grupo, BufferedImage fonte, List<Comp> todas, Tesseract tesseract) {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = -1, y1 = -1;
        for (Comp c : grupo) {
            x0 = Math.min(x0, c.x0);
            y0 = Math.min(y0, c.y0);
            x1 = Math.max(x1, c.x1);
            y1 = Math.max(y1, c.y1);
        }
        int alt = y1 - y0 + 1, pad = Math.max(2, alt / 3);
        int rx = Math.max(0, x0 - pad), ry = Math.max(0, y0 - pad);
        int rw = Math.min(fonte.getWidth(), x1 + pad + 1) - rx, rh = Math.min(fonte.getHeight(), y1 + pad + 1) - ry;
        // recorte em tons de cinza (com o antisserrilhado, que num dígito de ~7 px é boa parte da forma);
        // manchas vizinhas que invadem o recorte são apagadas
        BufferedImage recorte = new BufferedImage(rw + 2 * alt, rh + 2 * alt, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = recorte.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, recorte.getWidth(), recorte.getHeight());
        g.drawImage(fonte.getSubimage(rx, ry, rw, rh), alt, alt, null);
        g.dispose();
        for (Comp c : todas) {
            if (grupo.contains(c) || c.x1 < rx || c.x0 >= rx + rw || c.y1 < ry || c.y0 >= ry + rh) continue;
            for (int[] p : c.px) {
                int x = p[0] - rx + alt, y = p[1] - ry + alt;
                if (x >= 0 && y >= 0 && x < recorte.getWidth() && y < recorte.getHeight()) recorte.setRGB(x, y, 0xFFFFFF);
            }
        }
        // votação em vários tamanhos: um dígito miúdo trocado (8 -> 3) parece válido numa leitura só
        tesseract.setVariable("tessedit_char_whitelist", "0123456789-");
        java.util.Map<String, Integer> votos = new java.util.HashMap<>();
        for (int alvo : new int[]{32, 40, 48, 60, 72}) {
            String t = lerDigitos(redimensionar(recorte, Math.max(1.0, alvo / (double) alt)), grupo.size(), tesseract);
            if (t != null) votos.merge(t, 1, Integer::sum);
        }
        tesseract.setVariable("tessedit_char_whitelist", "");
        String lido = null;
        int primeiro = 0, segundo = 0;
        for (var e : votos.entrySet()) {
            if (e.getValue() > primeiro) {
                segundo = primeiro;
                primeiro = e.getValue();
                lido = e.getKey();
            } else if (e.getValue() > segundo) {
                segundo = e.getValue();
            }
        }
        if (primeiro < 3 || primeiro < 2 * segundo) lido = null;
        if (lido == null) return null;
        StringBuilder sb = new StringBuilder();
        for (char ch : lido.toCharArray()) sb.append(ch == '-' ? '⁻' : SUP.charAt(ch - '0'));
        return sb.toString();
    }

    /** Um número de {@code manchas} dígitos ("-" conta como uma mancha), ou null. */
    private static String lerDigitos(BufferedImage img, int manchas, Tesseract tesseract) {
        for (int psm : new int[]{manchas == 1 ? 10 : 8, 7, 13, 6}) {
            tesseract.setPageSegMode(psm);
            List<Word> ws = tesseract.getWords(img, TessPageIteratorLevel.RIL_WORD);
            if (ws.isEmpty()) continue;
            String t = ws.get(0).getText().replaceAll("\\s", "");
            if (t.matches("-?\\d{1,3}") && t.length() == manchas && ws.get(0).getConfidence() >= 40) return t;
        }
        return null;
    }

    /** Desenha só as manchas dadas, em preto sobre branco, numa imagem com origem em (ox, oy). */
    private static BufferedImage pintar(List<Comp> comps, int ox, int oy, int w, int h) {
        BufferedImage img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.dispose();
        for (Comp c : comps) {
            for (int[] p : c.px) {
                int x = p[0] - ox, y = p[1] - oy;
                if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, p[2]);
            }
        }
        return img;
    }

    private static BufferedImage ampliar(BufferedImage src) {
        return redimensionar(src, ESCALA);
    }

    private static BufferedImage redimensionar(BufferedImage src, double fator) {
        int w = (int) Math.round(src.getWidth() * fator), h = (int) Math.round(src.getHeight() * fator);
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return dst;
    }

    /** Componentes conexos (vizinhança 8). */
    private static List<Comp> componentes(boolean[][] tinta, BufferedImage fonte) {
        int h = tinta.length, w = tinta[0].length;
        boolean[][] visto = new boolean[h][w];
        List<Comp> saida = new ArrayList<>();
        ArrayDeque<int[]> fila = new ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (!tinta[y][x] || visto[y][x]) continue;
                Comp c = new Comp();
                visto[y][x] = true;
                fila.add(new int[]{x, y});
                while (!fila.isEmpty()) {
                    int[] p = fila.poll();
                    c.add(p[0], p[1], fonte.getRGB(p[0], p[1]));
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int nx = p[0] + dx, ny = p[1] + dy;
                            if (nx >= 0 && ny >= 0 && nx < w && ny < h && tinta[ny][nx] && !visto[ny][nx]) {
                                visto[ny][nx] = true;
                                fila.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
                saida.add(c);
            }
        }
        return saida;
    }

    private static double mediana(List<Double> v) {
        List<Double> s = new ArrayList<>(v);
        s.sort(Comparator.naturalOrder());
        return s.get(s.size() / 2);
    }
}
