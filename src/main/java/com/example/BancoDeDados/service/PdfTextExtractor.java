package com.example.BancoDeDados.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Extrai o texto de um PDF de prova/caderno para o fluxo de questões. Resolve três problemas do
 * {@link PDFTextStripper} puro:
 * <ol>
 *   <li><b>Ordem de leitura:</b> ordena por posição (alternativas deixam de aparecer depois do
 *       enunciado seguinte) e, em páginas de duas colunas (ex.: coluna "RASCUNHO" ao lado), lê uma
 *       coluna por vez em vez de misturar as duas na mesma linha.</li>
 *   <li><b>Símbolos:</b> parênteses, sinais e operadores de equações vêm da fonte Symbol como
 *       caracteres privados (U+F0xx) que viram "□" na tela; são convertidos para Unicode.</li>
 *   <li><b>Raiz quadrada:</b> o sinal de raiz de equações é um traço desenhado, não um caractere, e
 *       some do texto ("3√2" vira "3 2"). Onde há o vão típico de um radical entre dois glifos
 *       da mesma fonte de equação, o "√" é reinserido.</li>
 * </ol>
 */
public final class PdfTextExtractor {

    private static final double FAIXA_CABECALHO_RODAPE = 0.08;
    private static final double LARGURA_MIN_CALHA = 10.0; // pt
    private static final double CALHA_DE = 0.40, CALHA_ATE = 0.65;

    private PdfTextExtractor() {
    }

    public static String extrair(PDDocument doc) throws IOException {
        StringBuilder saida = new StringBuilder();
        for (int p = 1; p <= doc.getNumberOfPages(); p++) {
            PDPage page = doc.getPage(p - 1);
            double largura = page.getCropBox().getWidth(), altura = page.getCropBox().getHeight();
            Double calha = achaCalha(doc, p, largura, altura);
            Map<String, AnaliseMatematica.Anotacao> anot = AnaliseMatematica.analisar(doc, p);
            if (calha == null) {
                saida.append(texto(doc, p, null, anot));
            } else {
                double topo = altura * FAIXA_CABECALHO_RODAPE, base = altura * (1 - FAIXA_CABECALHO_RODAPE);
                saida.append(texto(doc, p, new Rectangle2D.Double(0, 0, largura, topo), anot));                  // cabeçalho
                saida.append(texto(doc, p, new Rectangle2D.Double(0, topo, calha, base - topo), anot));          // coluna esquerda
                saida.append(texto(doc, p, new Rectangle2D.Double(calha, topo, largura - calha, base - topo), anot)); // direita
                saida.append(texto(doc, p, new Rectangle2D.Double(0, base, largura, altura - base), anot));      // rodapé
            }
            saida.append('\n');
        }
        return normalizarSimbolos(saida.toString());
    }

    private static String texto(PDDocument doc, int pagina, Rectangle2D area,
                                Map<String, AnaliseMatematica.Anotacao> anot) throws IOException {
        MatematicaStripper s = new MatematicaStripper(area, anot);
        s.setSortByPosition(true);
        s.setStartPage(pagina);
        s.setEndPage(pagina);
        return s.getText(doc);
    }

    /**
     * Procura uma faixa vertical vazia no meio da página (a "calha" entre duas colunas) que nenhum
     * texto do corpo atravessa. Devolve o x do centro dela, ou null se a página for de uma coluna só.
     */
    private static Double achaCalha(PDDocument doc, int pagina, double largura, double altura) throws IOException {
        List<double[]> glifos = new ArrayList<>(); // {x, largura, y}
        PDFTextStripper coletor = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition t) {
                glifos.add(new double[]{t.getXDirAdj(), t.getWidthDirAdj(), t.getYDirAdj()});
            }
        };
        coletor.setStartPage(pagina);
        coletor.setEndPage(pagina);
        coletor.getText(doc);

        int bins = (int) Math.ceil(largura);
        boolean[] ocupado = new boolean[bins + 1];
        int corpo = 0, esquerda = 0;
        double topo = altura * FAIXA_CABECALHO_RODAPE, base = altura * (1 - FAIXA_CABECALHO_RODAPE);
        for (double[] g : glifos) {
            if (g[2] < topo || g[2] > base) continue; // cabeçalho/rodapé costumam cruzar a calha
            corpo++;
            for (int x = Math.max(0, (int) g[0]); x <= Math.min(bins, (int) Math.ceil(g[0] + g[1])); x++) ocupado[x] = true;
            if (g[0] < largura * CALHA_DE) esquerda++;
        }
        if (corpo < 40 || esquerda < corpo * 0.25) return null;

        int melhorIni = -1, melhorTam = 0, ini = -1;
        for (int x = (int) (largura * CALHA_DE); x <= (int) (largura * CALHA_ATE); x++) {
            if (!ocupado[x]) {
                if (ini < 0) ini = x;
                if (x - ini + 1 > melhorTam) {
                    melhorTam = x - ini + 1;
                    melhorIni = ini;
                }
            } else {
                ini = -1;
            }
        }
        return melhorTam >= LARGURA_MIN_CALHA ? melhorIni + melhorTam / 2.0 : null;
    }

    /** Stripper que (a) só considera os glifos dentro de uma área e (b) reinsere o radical perdido. */
    private static final class MatematicaStripper extends PDFTextStripper {
        private final Rectangle2D area;
        private static final int NENHUM = 0, RADICAL_COLADO = 1, RADICAL_APOS_TEXTO = 2;
        private TextPosition ultimaDaPalavra;     // último glifo da palavra anterior, na mesma linha
        private boolean separadorPendente;

        private final Map<String, AnaliseMatematica.Anotacao> anotacoes;

        MatematicaStripper(Rectangle2D area, Map<String, AnaliseMatematica.Anotacao> anotacoes) throws IOException {
            this.area = area;
            this.anotacoes = anotacoes;
        }

        @Override
        protected void processTextPosition(TextPosition t) {
            if (area != null && !area.contains(t.getXDirAdj(), t.getYDirAdj())) return;
            // expoentes, frações e raízes (ver AnaliseMatematica): reescreve/move o glifo ou o descarta
            AnaliseMatematica.Anotacao a = anotacoes.get(AnaliseMatematica.chave(t));
            if (a != null) {
                if (a.suprimir()) return;
                t = AnaliseMatematica.derivar(t, a.texto(), a.dy(), a.dx(), a.dw());
            }
            super.processTextPosition(t);
        }

        @Override
        protected void writeWordSeparator() throws IOException {
            separadorPendente = true; // decidido em writeString, quando se sabe o que vem depois
        }

        @Override
        protected void writeLineSeparator() throws IOException {
            separadorPendente = false;
            ultimaDaPalavra = null;
            super.writeLineSeparator();
        }

        @Override
        protected void writeString(String texto, List<TextPosition> posicoes) throws IOException {
            if (posicoes.isEmpty()) {
                super.writeString(texto, posicoes);
                return;
            }
            TextPosition primeira = posicoes.get(0);
            if (separadorPendente) {
                int radical = faltaRadicalEntre(ultimaDaPalavra, primeira);
                if (radical == RADICAL_COLADO) {
                    output.write("√");
                } else if (radical == RADICAL_APOS_TEXTO) {
                    output.write(getWordSeparator() + "√");
                } else {
                    output.write(getWordSeparator());
                }
                separadorPendente = false;
            }
            super.writeString(texto, posicoes);
            ultimaDaPalavra = posicoes.get(posicoes.size() - 1);
        }

        /**
         * O radical é um traço, não um glifo: sobra um vão do tamanho dele entre dois glifos alfanuméricos
         * da mesma fonte de equação (diferente da fonte do texto corrido). Ex.: "3" e "2" em "3√2".
         */
        private static int faltaRadicalEntre(TextPosition antes, TextPosition depois) {
            if (antes == null || depois == null || antes.getFont() == null || depois.getFont() == null) return NENHUM;
            if (!fonteDeEquacao(depois) || !alfanumerico(depois.getUnicode())) return NENHUM;
            if (Math.abs(antes.getYDirAdj() - depois.getYDirAdj()) > 3) return NENHUM;
            double vao = depois.getXDirAdj() - (antes.getXDirAdj() + antes.getWidthDirAdj());
            double larguraGlifo = Math.max(antes.getWidthDirAdj(), depois.getWidthDirAdj());

            // "3√2": dois glifos alfanuméricos da mesma fonte de equação, com o vão do radical no meio
            if (antes.getFont() == depois.getFont() && alfanumerico(antes.getUnicode())) {
                return vao >= 0.9 * larguraGlifo && vao <= 2.2 * larguraGlifo ? RADICAL_COLADO : NENHUM;
            }
            // "2+√3", "(√5": o radical vem logo depois de um operador/parêntese (fonte Symbol)
            if (operadorSymbol(antes)) {
                return vao >= 0.9 * depois.getWidthDirAdj() && vao <= 2.2 * depois.getWidthDirAdj() ? RADICAL_COLADO : NENHUM;
            }
            // "lado √11", "C) √13": equação começando depois de texto corrido. Num espaço normal o vão é
            // ~0,4 da largura do glifo; com radical fica em ~1,5 (medido no PDF do Colégio Militar).
            if (!fonteDeEquacao(antes) && !antes.getFont().getName().toLowerCase(Locale.ROOT).contains("symbol")) {
                return vao >= 1.0 * depois.getWidthDirAdj() && vao <= 2.5 * depois.getWidthDirAdj() ? RADICAL_APOS_TEXTO : NENHUM;
            }
            return NENHUM;
        }

        private static boolean fonteDeEquacao(TextPosition t) {
            String f = t.getFont().getName() == null ? "" : t.getFont().getName().toLowerCase(Locale.ROOT);
            return f.contains("times") || f.contains("cambria") || f.contains("math");
        }

        private static boolean operadorSymbol(TextPosition t) {
            String f = t.getFont().getName() == null ? "" : t.getFont().getName().toLowerCase(Locale.ROOT);
            String u = t.getUnicode();
            if (!f.contains("symbol") || u == null || u.length() != 1) return false;
            return switch (u.charAt(0) & 0xFF) { case 0x2B, 0x2D, 0x3D, 0x28, 0x3C, 0x3E, 0xD7, 0xB4 -> true; default -> false; };
        }

        private static boolean alfanumerico(String s) {
            return s != null && s.length() == 1 && Character.isLetterOrDigit(s.charAt(0));
        }
    }

    // ------------------------------------------------------------------ símbolos

    /** Converte os caracteres privados da fonte Symbol/Wingdings (U+F0xx) para Unicode. */
    static String normalizarSimbolos(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0xF000 || c > 0xF0FF) {
                sb.append(c);
                continue;
            }
            int baixo = c & 0xFF;
            switch (baixo) {
                case 0xD6 -> sb.append('√');          // √
                case 0xD7 -> sb.append('·');          // ·
                case 0xB4 -> sb.append('×');          // ×
                case 0xB1 -> sb.append('±');          // ±
                case 0xA3 -> sb.append('≤');          // ≤
                case 0xB3 -> sb.append('≥');          // ≥
                case 0xB9 -> sb.append('≠');          // ≠
                case 0xBB -> sb.append('≈');          // ≈
                case 0xA5 -> sb.append('∞');          // ∞
                case 0xB0 -> sb.append('°');          // °
                case 0xD0 -> sb.append('∠');          // ∠
                case 0xAE -> sb.append('→');          // →
                case 0xCE -> sb.append('∈');          // ∈
                case 0xC7 -> sb.append('∩');          // ∩
                case 0xC8 -> sb.append('∪');          // ∪
                case 0xE5 -> sb.append('∑');          // ∑
                case 0xF2 -> sb.append('∫');          // ∫
                case 0xE6, 0xE9 -> sb.append(baixo == 0xE6 ? '(' : '[');   // topo de parêntese/colchete grande
                case 0xF6, 0xF9 -> sb.append(baixo == 0xF6 ? ')' : ']');
                case 0xE7, 0xE8, 0xEA, 0xEB, 0xF7, 0xF8, 0xFA, 0xFB -> { /* meio/base do delimitador grande: descarta */ }
                default -> {
                    // faixa ASCII da fonte Symbol coincide com o ASCII ((, ), +, -, =, <, >, dígitos...)
                    if (baixo >= 0x20 && baixo <= 0x7E) sb.append((char) baixo);
                    // demais (Wingdings: marcadores ❖ ▪ etc.): descarta
                }
            }
        }
        return sb.toString();
    }
}
