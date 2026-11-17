package com.example.BancoDeDados.service;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recupera a matemática que o texto do PDF perde: expoentes (x² vira "x2"), frações (numerador e
 * denominador em linhas separadas), raízes (o sinal √ é um polígono desenhado, não um caractere) e
 * delimitadores grandes (parênteses montados com 3 peças em alturas diferentes).
 *
 * Trabalha por página em duas fontes de informação: os glifos (posição e tamanho) e o desenho vetorial
 * (barras de fração e polígonos de radical). O resultado é um mapa glifo → anotação, usado pelo
 * {@link PdfTextExtractor} para reescrever/descartar glifos antes de o PDFBox montar as linhas.
 *
 * Expoentes e índices são <b>fundidos no glifo da base</b> ("1" + "²" = "1²") em vez de movidos para a
 * linha de base: um glifo solto no fim de uma linha podia ser jogado pelo PDFBox para uma linha só dele.
 */
final class AnaliseMatematica {

    /**
     * O que fazer com um glifo: suprimi-lo, trocar o texto, mover (dy>0 = para baixo, dx<0 = para a esquerda)
     * e/ou alargar à direita (dw), para que o PDFBox calcule corretamente os espaços ao redor.
     */
    record Anotacao(boolean suprimir, String texto, double dy, double dx, double dw) {
    }

    private AnaliseMatematica() {
    }

    static String chave(TextPosition t) {
        return String.format(Locale.ROOT, "%.2f|%.2f|%s", t.getXDirAdj(), t.getYDirAdj(), t.getUnicode());
    }

    /** Nova posição de texto, com outro conteúdo e/ou deslocada/alargada. */
    static TextPosition derivar(TextPosition t, String texto, double dy, double dx, double dw) {
        Matrix m = t.getTextMatrix().clone();
        m.setValue(2, 1, (float) (m.getValue(2, 1) - dy)); // y do PDF cresce para cima
        m.setValue(2, 0, (float) (m.getValue(2, 0) + dx));
        return new TextPosition(t.getRotation(), t.getPageWidth(), t.getPageHeight(), m,
                (float) (t.getEndX() + dw), (float) (t.getEndY() - dy), t.getHeight(), t.getWidth() + (float) dw,
                t.getWidthOfSpace(), texto, t.getCharacterCodes(), t.getFont(), t.getFontSize(),
                (int) t.getFontSizeInPt());
    }

    // ------------------------------------------------------------------ modelo interno

    private static final class Glifo {
        final TextPosition t;
        final String chave;
        double x, xFim;                 // crescem quando um expoente é fundido na base
        final double y, fs;
        String texto;                   // o que será escrito (já com expoentes fundidos, prefixos de raiz etc.)
        final String bruto;             // caractere original normalizado (Symbol -> ASCII/Unicode)
        double dy, dx, dw;
        boolean suprimido;
        boolean sobrescrito, subscrito; // consumido como expoente/índice
        boolean reservado;              // índice de raiz: não pode virar expoente
        boolean grande;                 // peça de delimitador grande (parêntese, colchete, chave)
        boolean composto;               // fração já montada neste glifo

        Glifo(TextPosition t) {
            this.t = t;
            this.chave = AnaliseMatematica.chave(t);
            this.x = t.getXDirAdj();
            this.xFim = t.getXDirAdj() + t.getWidthDirAdj();
            this.y = t.getYDirAdj();
            this.fs = Math.max(1, t.getFontSizeInPt());
            this.bruto = PdfTextExtractor.normalizarSimbolos(t.getUnicode());
            this.texto = this.bruto;
        }

        double centro() {
            return (x + xFim) / 2;
        }

        double yFinal() {
            return y + dy;
        }

        /** Linha de base de referência para medir elevação: peças de delimitador grande usam a posição original. */
        double yRef() {
            return grande ? y : yFinal();
        }
    }

    private record Barra(double x1, double x2, double y) {
    }

    private record Radical(double minX, double maxX, double barX1, double barX2, double barY, double baseY) {
    }

    // ------------------------------------------------------------------ análise da página

    static Map<String, Anotacao> analisar(PDDocument doc, int pagina) throws IOException {
        PDPage page = doc.getPage(pagina - 1);
        Map<String, Anotacao> saida = new HashMap<>();
        if (page.getRotation() != 0) return saida; // só páginas sem rotação

        List<Glifo> glifos = new ArrayList<>();
        PDFTextStripper coletor = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition t) {
                if (t.getUnicode() != null && !t.getUnicode().isBlank()) glifos.add(new Glifo(t));
            }
        };
        coletor.setStartPage(pagina);
        coletor.setEndPage(pagina);
        coletor.getText(doc);
        if (glifos.isEmpty()) return saida;

        ColetorDeTracos tracos = new ColetorDeTracos(page);
        try {
            tracos.processPage(page);
        } catch (Exception e) {
            // página com desenho que o motor não entende: segue só com os expoentes
        }

        // texto dentro de uma moldura de gráfico vetorial: o gráfico vira imagem (ver PDFImageExtractorService),
        // então os rótulos de eixo e títulos não podem aparecer no enunciado da questão
        for (java.awt.geom.Rectangle2D.Double moldura : FigurasDeAlternativas.molduraDeGraficosVetoriais(page)) {
            for (Glifo g : glifos) {
                if (moldura.contains(g.centro(), g.y - g.fs * 0.3)) g.suprimido = true;
            }
        }

        delimitadoresGrandes(glifos);
        reservarIndicesDeRaiz(glifos, tracos.radicais);
        radicais(glifos, tracos.radicais);

        // a barra de uma raiz também aparece como retângulo fino: não é barra de fração
        List<Barra> soFracoes = new ArrayList<>();
        for (Barra b : tracos.barras) {
            boolean deRadical = false;
            for (Radical r : tracos.radicais) {
                if (Math.abs(b.y - r.barY) <= 1.5 && b.x1 < r.barX2 + 1 && b.x2 > r.barX1 - 1) {
                    deRadical = true;
                    break;
                }
            }
            if (!deRadical) soFracoes.add(b);
        }
        fracoes(glifos, soFracoes);
        dobrarExpoentes(glifos);

        for (Glifo g : glifos) {
            boolean mudou = g.suprimido || g.dy != 0 || g.dx != 0 || g.dw != 0 || !g.texto.equals(g.t.getUnicode());
            if (mudou) saida.put(g.chave, new Anotacao(g.suprimido, g.texto, g.dy, g.dx, g.dw));
        }
        return saida;
    }

    // ------------------------------------------------------------------ delimitadores grandes

    /**
     * Parênteses/colchetes/chaves altos são montados com peças (topo, meio, base) em linhas diferentes. Fica só o
     * topo, escrito como "(" ou ")" e levado para a linha de base da expressão; meio e base somem.
     */
    private static void delimitadoresGrandes(List<Glifo> glifos) {
        for (Glifo topo : glifos) {
            String s = topo.t.getUnicode();
            if (topo.suprimido || s.length() != 1 || s.charAt(0) < 0xF000 || s.charAt(0) > 0xF0FF) continue;
            int b = s.charAt(0) & 0xFF;
            String simbolo = switch (b) {
                case 0xE6 -> "(";
                case 0xF6 -> ")";
                case 0xE9 -> "[";
                case 0xF9 -> "]";
                case 0xEC -> "{";
                case 0xFC -> "}";
                default -> null;
            };
            if (simbolo == null) continue;
            // as peças de meio e de base ficam logo depois do topo na tabela da fonte Symbol (E6,E7,E8 / F6,F7,F8 ...)
            int meio = b + 1, fundo = b + 2;
            List<Glifo> pecas = new ArrayList<>();
            double yFundo = topo.y;
            for (Glifo p : glifos) {
                if (p == topo || p.suprimido) continue;
                String ps = p.t.getUnicode();
                if (ps.length() != 1 || ps.charAt(0) < 0xF000 || ps.charAt(0) > 0xF0FF) continue;
                int pb = ps.charAt(0) & 0xFF;
                if ((pb == meio || pb == fundo) && Math.abs(p.x - topo.x) < 1.5 && p.y > topo.y && p.y - topo.y < 120) {
                    pecas.add(p);
                    yFundo = Math.max(yFundo, p.y);
                }
            }
            topo.grande = true;
            topo.texto = simbolo;
            for (Glifo p : pecas) p.suprimido = true;

            // linha de base da expressão: a mais comum entre os glifos "normais" ao lado do delimitador
            List<Double> ys = new ArrayList<>();
            for (Glifo p : glifos) {
                if (p.suprimido || p == topo || p.fs < 0.9 * topo.fs) continue;
                if (p.y >= topo.y - 2 && p.y <= yFundo + 2 && Math.abs(p.x - topo.x) <= 150) ys.add(p.y);
            }
            double ref;
            if (!ys.isEmpty()) {
                ys.sort(Comparator.naturalOrder());
                ref = ys.get(ys.size() / 2);
            } else {
                ref = (topo.y + yFundo) / 2 - 0.27 * topo.fs;
            }
            topo.dy = ref - topo.y;
        }
    }

    // ------------------------------------------------------------------ expoentes e índices

    private static boolean possivelExpoente(Glifo g) {
        if (g.composto) return true;
        String s = g.bruto;
        return s.length() == 1 && (Character.isLetterOrDigit(s.charAt(0)) || "+-−".indexOf(s.charAt(0)) >= 0)
                && "ºª°".indexOf(s.charAt(0)) < 0;
    }

    /**
     * Funde expoentes e índices no glifo da base. A base é o glifo maior imediatamente à esquerda (quase
     * encostado, na mesma linha); o expoente é menor e fica acima, o índice é menor e fica abaixo. Vários
     * glifos pequenos seguidos (10^12, 6^-1/3) formam um expoente só.
     */
    private static void dobrarExpoentes(List<Glifo> lista) {
        List<Glifo> porX = new ArrayList<>(lista);
        porX.sort(Comparator.comparingDouble(g -> g.x));
        for (Glifo g : porX) {
            if (g.suprimido || g.sobrescrito || g.subscrito || g.reservado || !possivelExpoente(g)) continue;
            // base: glifo maior colado à esquerda e na mesma linha (até 1,2 em de diferença na vertical;
            // mais que isso já é a linha de cima ou de baixo)
            Glifo base = null;
            for (Glifo p : porX) {
                if (p == g || p.suprimido || p.sobrescrito || p.subscrito || p.x > g.x || !baseValida(p)) continue;
                double vao = g.x - p.xFim; // letra itálica: o índice pode começar um pouco antes do fim da base
                if (vao < -2.5 || vao > 0.6 * p.fs) continue;
                if (g.fs > 0.82 * p.fs || g.fs < 0.45 * p.fs) continue; // expoente típico: 55-70% do tamanho da base
                double dv = p.yRef() - g.yFinal(); // >0: g mais alto que a base
                if (dv < -0.9 * p.fs || dv > 1.2 * p.fs) continue;
                if (base == null || p.xFim > base.xFim) base = p;
            }
            if (base == null) continue;
            double elevacao = base.yRef() - g.yFinal();
            boolean sobe;
            if (elevacao >= 0.25 * base.fs) sobe = true;
            else if (-elevacao >= 0.2 * base.fs && -elevacao <= 0.8 * base.fs) sobe = false;
            else continue;

            // o expoente inteiro: este glifo e os pequenos que vêm colados em seguida, no mesmo nível
            List<Glifo> partes = new ArrayList<>();
            partes.add(g);
            Glifo ultimo = g;
            for (Glifo h : porX) {
                if (h == g || h.suprimido || h.sobrescrito || h.subscrito || h.reservado || !possivelExpoente(h) || h.x < ultimo.x) continue;
                if (h.x - ultimo.xFim < -1.0 || h.x - ultimo.xFim > 0.5 * h.fs) continue;
                if (Math.abs(h.yFinal() - g.yFinal()) > 2.0 || h.fs > 0.82 * base.fs) continue;
                partes.add(h);
                ultimo = h;
            }

            // expoentes e índices são curtos; uma "palavra" miúda colada a um glifo grande é legenda/título
            if (partes.size() > 4) continue;
            // e só dígitos/sinais formam expoentes longos; mais de duas letras seguidas é palavra, não variável
            boolean temDigitoOuSinal = false;
            for (Glifo p : partes) {
                if (p.composto || !Character.isLetter(p.bruto.isEmpty() ? 'x' : p.bruto.charAt(0))) temDigitoOuSinal = true;
            }
            if (!temDigitoOuSinal && partes.size() > 2) continue;

            boolean tudoMapeavel = true;
            StringBuilder cru = new StringBuilder(), mapeado = new StringBuilder();
            for (Glifo p : partes) {
                cru.append(p.texto);
                String m = !p.composto && p.bruto.length() == 1 ? (sobe ? sobrescrito(p.bruto) : subscrito(p.bruto)) : null;
                if (m == null) tudoMapeavel = false;
                else mapeado.append(m);
            }
            String escrito = tudoMapeavel ? mapeado.toString()
                    : (sobe ? "^" : "_") + (cru.length() > 1 ? "(" + cru + ")" : cru);
            base.texto = base.texto + escrito;
            double alarga = ultimo.xFim - base.xFim;
            if (alarga > 0) {
                base.dw += alarga;
                base.xFim = ultimo.xFim;
            }
            for (Glifo p : partes) {
                p.suprimido = true;
                if (sobe) p.sobrescrito = true;
                else p.subscrito = true;
            }
        }
    }

    /** Pontuação (ponto, vírgula...) nunca é base de expoente. */
    private static boolean baseValida(Glifo p) {
        if (p.grande || p.composto) return true;
        String b = p.bruto;
        if (b.length() != 1) return false;
        char c = b.charAt(0);
        return Character.isLetterOrDigit(c) || ")]}".indexOf(c) >= 0;
    }

    private static final String SUP_DIGITOS = "⁰¹²³⁴⁵⁶⁷⁸⁹";
    private static final String SUB_DIGITOS = "₀₁₂₃₄₅₆₇₈₉";

    /** Caractere em sobrescrito Unicode, ou null se não existir. */
    private static String sobrescrito(String c) {
        char ch = c.charAt(0);
        if (ch >= '0' && ch <= '9') return String.valueOf(SUP_DIGITOS.charAt(ch - '0'));
        return switch (ch) {
            case '+' -> "⁺";
            case '-', '−' -> "⁻";
            case 'n' -> "ⁿ";
            case 'i' -> "ⁱ";
            default -> null;
        };
    }

    private static String subscrito(String c) {
        char ch = c.charAt(0);
        if (ch >= '0' && ch <= '9') return String.valueOf(SUB_DIGITOS.charAt(ch - '0'));
        return switch (ch) {
            case '+' -> "₊";
            case '-', '−' -> "₋";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ raízes

    private static List<Glifo> radicando(List<Glifo> glifos, Radical r) {
        List<Glifo> lista = new ArrayList<>();
        for (Glifo g : glifos) {
            if (g.suprimido || g.reservado) continue;
            if (g.centro() >= r.barX1 - 0.5 && g.centro() <= r.barX2 + 0.5
                    && g.yFinal() >= r.barY - 0.5 && g.yFinal() <= r.baseY + 2.5) {
                lista.add(g);
            }
        }
        return lista;
    }

    /** O índice de ∛/∜ (glifo pequeno, no alto, à esquerda do gancho) precisa ser separado antes dos expoentes. */
    private static Glifo indiceDaRaiz(List<Glifo> glifos, Radical r, List<Glifo> radicando) {
        if (radicando.isEmpty()) return null;
        double fsRadicando = radicando.stream().mapToDouble(g -> g.fs).max().orElse(11);
        Glifo indice = null;
        for (Glifo g : glifos) {
            if (g.suprimido || radicando.contains(g)) continue;
            if (g.xFim >= r.minX - 8 && g.x <= r.minX + 3 && g.yFinal() < r.barY + 0.6 * (r.baseY - r.barY)
                    && g.yFinal() > r.barY - 6 && g.fs <= 0.85 * fsRadicando && g.bruto.length() == 1
                    && Character.isDigit(g.bruto.charAt(0))) {
                indice = g;
            }
        }
        return indice;
    }

    private static boolean radicalValido(List<Glifo> radicando, Radical r) {
        double largura = 0;
        for (Glifo g : radicando) largura += g.xFim - g.x;
        return !radicando.isEmpty() && largura >= 0.6 * (r.barX2 - r.barX1);
    }

    private static void reservarIndicesDeRaiz(List<Glifo> glifos, List<Radical> radicais) {
        for (Radical r : radicais) {
            List<Glifo> radicando = radicando(glifos, r);
            if (!radicalValido(radicando, r)) continue;
            Glifo indice = indiceDaRaiz(glifos, r, radicando);
            if (indice != null) indice.reservado = true;
        }
    }

    private static void radicais(List<Glifo> glifos, List<Radical> radicais) {
        for (Radical r : radicais) {
            List<Glifo> radicando = radicando(glifos, r);
            if (!radicalValido(radicando, r)) continue; // desenho, não radical
            Glifo indice = indiceDaRaiz(glifos, r, radicando);

            // expoentes dentro do radicando (√(x²)) entram no texto antes de montá-lo
            dobrarExpoentes(radicando);
            radicando.removeIf(g -> g.suprimido);
            if (radicando.isEmpty()) continue;
            radicando.sort(Comparator.comparingDouble(g -> g.x));

            String prefixo = "√";
            if (indice != null) {
                prefixo = switch (indice.bruto.charAt(0)) {
                    case '3' -> "∛";
                    case '4' -> "∜";
                    default -> sobrescrito(indice.bruto) == null ? indice.bruto + "√" : sobrescrito(indice.bruto) + "√";
                };
                indice.suprimido = true;
            }

            StringBuilder interno = new StringBuilder();
            for (Glifo g : radicando) interno.append(g.texto);
            boolean simples = interno.toString().matches("[\\p{L}\\p{N}.,]+");
            Glifo primeiro = radicando.get(0), ultimo = radicando.get(radicando.size() - 1);
            primeiro.texto = prefixo + (simples ? "" : "(") + primeiro.texto;
            if (!simples) ultimo.texto = ultimo.texto + ")";
            double recuo = r.minX - primeiro.x; // o gancho ocupa o espaço entre o coeficiente e o radicando
            if (recuo < 0 && recuo > -14) primeiro.dx = recuo; // o fim do glifo fica onde estava: ele "alarga" para a esquerda
        }
    }

    // ------------------------------------------------------------------ frações

    private static void fracoes(List<Glifo> glifos, List<Barra> barras) {
        for (Barra b : barras) {
            double comp = b.x2 - b.x1;
            if (comp < 3 || comp > 150) continue; // linhas de tabela/figura são longas
            List<Glifo> num = new ArrayList<>(), den = new ArrayList<>();
            for (Glifo g : glifos) {
                if (g.suprimido || g.reservado || g.centro() < b.x1 - 2 || g.centro() > b.x2 + 2) continue;
                double acima = b.y - g.yRef(), abaixo = g.yRef() - b.y;
                if (acima > 0.2 && acima <= 1.3 * g.fs) num.add(g);
                else if (abaixo > 0.2 && abaixo <= 1.9 * g.fs) den.add(g);
            }
            if (num.isEmpty() || den.isEmpty()) continue;
            incluirExpoentesColados(glifos, num, b);

            // expoentes dentro de cada parte (x²/2) são resolvidos antes de medir e montar
            dobrarExpoentes(num);
            dobrarExpoentes(den);
            num.removeIf(g -> g.suprimido);
            den.removeIf(g -> g.suprimido);
            if (num.isEmpty() || den.isEmpty()) continue;

            double larguraNum = extensao(num), larguraDen = extensao(den);
            double maior = Math.max(larguraNum, larguraDen);
            if (maior < 0.6 * comp || maior > 1.35 * comp + 4) continue; // a barra acompanha o termo mais largo
            if (linha(num) > 4 || linha(den) > 4) continue;               // cada parte numa linha só
            // numerador/denominador de fração são curtos e sem palavras: moldura de texto e tabela ficam de fora
            if (num.size() > 14 || den.size() > 14 || temPalavra(num) || temPalavra(den)) continue;

            String n = montar(num), d = montar(den);
            Glifo primeiro = num.stream().min(Comparator.comparingDouble(g -> g.x)).get();
            for (Glifo g : num) if (g != primeiro) g.suprimido = true;
            for (Glifo g : den) g.suprimido = true;
            primeiro.texto = envolver(n) + "/" + envolver(d);
            primeiro.composto = true;
            primeiro.xFim = Math.max(extensaoFim(num), extensaoFim(den));
            // sobe/desce para a linha do texto ao redor: o eixo da fração fica ~0,3 em acima da linha de base
            primeiro.dy += (b.y + 0.3 * primeiro.fs) - primeiro.yFinal();
        }
    }

    /**
     * O expoente do numerador (x²/2) é pequeno e fica alto demais para o limite normal; entra se estiver
     * colado ao fim de um glifo já escolhido.
     */
    private static void incluirExpoentesColados(List<Glifo> glifos, List<Glifo> num, Barra b) {
        for (Glifo g : glifos) {
            if (g.suprimido || g.reservado || num.contains(g) || g.centro() < b.x1 - 2 || g.centro() > b.x2 + 2) continue;
            double acima = b.y - g.yRef();
            if (acima <= 0.2 || acima > 1.9 * g.fs) continue;
            for (Glifo n : new ArrayList<>(num)) {
                if (g.fs <= 0.82 * n.fs && g.x - n.xFim >= -1.0 && g.x - n.xFim <= 1.0 && n.yRef() > g.yRef()) {
                    num.add(g);
                    break;
                }
            }
        }
    }

    private static double extensao(List<Glifo> l) {
        double a = Double.MAX_VALUE, b = -Double.MAX_VALUE;
        for (Glifo g : l) {
            a = Math.min(a, g.x);
            b = Math.max(b, g.xFim);
        }
        return b - a;
    }

    private static double extensaoFim(List<Glifo> l) {
        double b = -Double.MAX_VALUE;
        for (Glifo g : l) b = Math.max(b, g.xFim);
        return b;
    }

    private static double linha(List<Glifo> l) {
        double a = Double.MAX_VALUE, b = -Double.MAX_VALUE;
        for (Glifo g : l) {
            a = Math.min(a, g.yRef());
            b = Math.max(b, g.yRef());
        }
        return a == Double.MAX_VALUE ? 0 : b - a;
    }

    /** Quatro letras seguidas = palavra de texto corrido, não variável/função de uma fração. */
    private static boolean temPalavra(List<Glifo> l) {
        return montar(l).matches("(?s).*\\p{L}{4,}.*");
    }

    private static String montar(List<Glifo> l) {
        List<Glifo> ord = new ArrayList<>(l);
        ord.sort(Comparator.comparingDouble(g -> g.x));
        StringBuilder sb = new StringBuilder();
        Glifo ant = null;
        for (Glifo g : ord) {
            if (ant != null && g.x - ant.xFim > 0.3 * g.fs) sb.append(' ');
            sb.append(g.texto);
            ant = g;
        }
        return sb.toString().trim();
    }

    private static String envolver(String s) {
        return s.matches("[\\p{L}\\p{N}.,²³¹⁰-⁹]{1,6}") ? s : "(" + s + ")";
    }

    // ------------------------------------------------------------------ desenho vetorial

    /** Percorre o desenho da página guardando as barras horizontais finas e os polígonos de radical. */
    private static final class ColetorDeTracos extends PDFGraphicsStreamEngine {
        final List<Barra> barras = new ArrayList<>();
        final List<Radical> radicais = new ArrayList<>();
        private final double altura, deslocX, deslocY;
        private final List<List<double[]>> subcaminhos = new ArrayList<>();
        private final List<boolean[]> fechados = new ArrayList<>();
        private final List<double[]> retangulos = new ArrayList<>();
        private double atualX, atualY;

        ColetorDeTracos(PDPage page) {
            super(page);
            this.altura = page.getCropBox().getHeight();
            this.deslocX = page.getCropBox().getLowerLeftX();
            this.deslocY = page.getCropBox().getLowerLeftY();
        }

        private double ax(double x) {
            return x - deslocX;
        }

        private double ay(double y) {
            return altura - (y - deslocY);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            double xa = Math.min(Math.min(p0.getX(), p1.getX()), Math.min(p2.getX(), p3.getX()));
            double xb = Math.max(Math.max(p0.getX(), p1.getX()), Math.max(p2.getX(), p3.getX()));
            double ya = Math.min(Math.min(p0.getY(), p1.getY()), Math.min(p2.getY(), p3.getY()));
            double yb = Math.max(Math.max(p0.getY(), p1.getY()), Math.max(p2.getY(), p3.getY()));
            retangulos.add(new double[]{ax(xa), ax(xb), ay(yb), ay(ya)}); // x1, x2, yTopo, yBase (ajustados)
        }

        @Override
        public void moveTo(float x, float y) {
            List<double[]> s = new ArrayList<>();
            s.add(new double[]{ax(x), ay(y)});
            subcaminhos.add(s);
            fechados.add(new boolean[]{false});
            atualX = x;
            atualY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            if (subcaminhos.isEmpty()) moveTo(x, y);
            else subcaminhos.get(subcaminhos.size() - 1).add(new double[]{ax(x), ay(y)});
            atualX = x;
            atualY = y;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            lineTo(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Double(atualX, atualY);
        }

        @Override
        public void closePath() {
            if (!fechados.isEmpty()) fechados.get(fechados.size() - 1)[0] = true;
        }

        @Override
        public void endPath() {
            limpar();
        }

        @Override
        public void strokePath() {
            for (List<double[]> s : subcaminhos) {
                if (s.size() == 2 && Math.abs(s.get(0)[1] - s.get(1)[1]) < 0.3) {
                    double a = Math.min(s.get(0)[0], s.get(1)[0]), b = Math.max(s.get(0)[0], s.get(1)[0]);
                    if (b - a >= 3) barras.add(new Barra(a, b, s.get(0)[1]));
                }
            }
            for (double[] r : retangulos) {
                if (r[3] - r[2] <= 1.6 && r[1] - r[0] >= 3) barras.add(new Barra(r[0], r[1], (r[2] + r[3]) / 2));
            }
            limpar();
        }

        @Override
        public void fillPath(int windingRule) {
            for (double[] r : retangulos) {
                if (r[3] - r[2] <= 1.6 && r[1] - r[0] >= 3) barras.add(new Barra(r[0], r[1], (r[2] + r[3]) / 2));
            }
            for (int i = 0; i < subcaminhos.size(); i++) {
                if (fechados.get(i)[0]) tentarRadical(subcaminhos.get(i));
            }
            limpar();
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            fillPath(windingRule);
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

        private void limpar() {
            subcaminhos.clear();
            fechados.clear();
            retangulos.clear();
        }

        /** Polígono fechado com 2+ diagonais e uma aresta horizontal no topo = sinal de raiz. */
        private void tentarRadical(List<double[]> p) {
            if (p.size() < 5) return;
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (double[] q : p) {
                minX = Math.min(minX, q[0]);
                maxX = Math.max(maxX, q[0]);
                minY = Math.min(minY, q[1]);
                maxY = Math.max(maxY, q[1]);
            }
            double h = maxY - minY;
            if (h < 4 || h > 40 || maxX - minX < 4 || maxX - minX > 400) return;
            int diagonais = 0;
            double barX1 = 0, barX2 = 0;
            double melhor = 0;
            for (int i = 0; i < p.size(); i++) {
                double[] a = p.get(i), b = p.get((i + 1) % p.size());
                double dx = Math.abs(b[0] - a[0]), dy = Math.abs(b[1] - a[1]);
                if (dy < 0.25 && dx >= 1.5 && Math.abs(a[1] - minY) < 0.5) {
                    if (dx > melhor) {
                        melhor = dx;
                        barX1 = Math.min(a[0], b[0]);
                        barX2 = Math.max(a[0], b[0]);
                    }
                } else if (dx > 0.4 && dy > 0.8) {
                    diagonais++;
                }
            }
            if (melhor >= 2 && diagonais >= 2) radicais.add(new Radical(minX, maxX, barX1, barX2, minY, maxY));
        }
    }
}
