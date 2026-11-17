package com.example.BancoDeDados.service;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serviço para extrair imagens embutidas de PDFs, junto com a posição de cada uma na página.
 * A posição é o que permite associar a imagem à questão correta (ver ImagemQuestaoAssociadorService).
 */
@Service
public class PDFImageExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(PDFImageExtractorService.class);

    // Imagens inteiramente nesta faixa do topo/rodapé da página são cabeçalho/rodapé, não figura de questão
    private static final double MARGEM_CABECALHO_RODAPE = 0.08;

    /** Imagem extraída e salva em disco, com a posição em que aparece na página (pode ser null). */
    public record ImagemExtraida(String caminho, PosicaoImagem posicao) {
    }

    /**
     * Extrai as imagens embutidas de um PDF que podem ser figuras de questões.
     * Ignora ícones, páginas escaneadas inteiras, imagens repetidas entre páginas
     * (logo, marca d'água) e imagens de cabeçalho/rodapé.
     *
     * @param pdfFile Arquivo PDF
     * @param outputDir Diretório onde salvar as imagens extraídas
     * @param prefix Prefixo para nomes dos arquivos
     */
    public List<ImagemExtraida> extractImagesFromPDF(File pdfFile, Path outputDir, String prefix) throws IOException {
        List<ImagemExtraida> extractedImages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            int imageCounter = 0;

            // Imagens que aparecem em mais de uma página (logo, cabeçalho, rodapé, marca d'água)
            // são decoração da página e não fazem parte de nenhuma questão
            Set<String> imagensDecorativas = detectRepeatedImages(document);

            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage page = document.getPage(pageNum);
                PDResources resources = page.getResources();
                if (resources == null) {
                    continue;
                }

                Map<String, PosicaoImagem> posicoes = detectImagePositions(page, pageNum + 1);

                for (COSName name : resources.getXObjectNames()) {
                    try {
                        PDXObject xObject = resources.getXObject(name);

                        if (!(xObject instanceof PDImageXObject)) {
                            continue;
                        }
                        PDImageXObject image = (PDImageXObject) xObject;

                        if (imagensDecorativas.contains(hashImage(image))) {
                            logger.debug("Ignorando imagem repetida entre páginas (decoração) na página {}", pageNum + 1);
                            continue;
                        }

                        int width = image.getWidth();
                        int height = image.getHeight();

                        // Filtrar imagens muito pequenas (ícones/logos) ou muito grandes (páginas escaneadas)
                        if (width < 50 || height < 50) {
                            logger.debug("Ignorando imagem pequena {}x{} (ícone/logo)", width, height);
                            continue;
                        }

                        // Páginas A4 em 150-300 DPI têm ~1500x2100 pixels ou mais
                        if (width > 1000 && height > 1400) {
                            logger.debug("Ignorando imagem grande {}x{} (parece página inteira escaneada)", width, height);
                            continue;
                        }

                        // Filtrar por aspect ratio (páginas têm ratio ~1.4, expressões são mais variadas)
                        double aspectRatio = (double) height / width;
                        if (aspectRatio > 1.3 && aspectRatio < 1.5 && width > 800) {
                            logger.debug("Ignorando imagem {}x{} (aspect ratio {} parece página A4)",
                                       width, height, String.format("%.2f", aspectRatio));
                            continue;
                        }

                        PosicaoImagem posicao = posicoes.get(name.getName());
                        if (posicao != null
                                && (posicao.base() <= MARGEM_CABECALHO_RODAPE
                                    || posicao.topo() >= 1.0 - MARGEM_CABECALHO_RODAPE)) {
                            logger.debug("Ignorando imagem de cabeçalho/rodapé na página {}", pageNum + 1);
                            continue;
                        }

                        String filename = String.format("%s-page%d-img%d.png",
                                                      prefix != null ? prefix : "image",
                                                      pageNum + 1,
                                                      imageCounter);
                        File outputFile = outputDir.resolve(filename).toFile();

                        BufferedImage bufferedImage = image.getImage();

                        // Uma imagem só com os cinco gráficos A..E: vira cinco imagens, na ordem de leitura
                        List<FigurasDeAlternativas.Painel> paineis = FigurasDeAlternativas.dividirComposta(bufferedImage);
                        if (paineis != null) {
                            for (FigurasDeAlternativas.Painel painel : paineis) {
                                String nomePainel = String.format("%s-page%d-img%d.png",
                                        prefix != null ? prefix : "image", pageNum + 1, imageCounter);
                                File arquivoPainel = outputDir.resolve(nomePainel).toFile();
                                ImageIO.write(painel.imagem(), "PNG", arquivoPainel);
                                extractedImages.add(new ImagemExtraida(arquivoPainel.getAbsolutePath(),
                                        subposicao(posicao, painel)));
                                imageCounter++;
                            }
                            logger.info("Imagem {}x{} da página {} dividida em 5 painéis (alternativas em figura)",
                                    width, height, pageNum + 1);
                            continue;
                        }

                        ImageIO.write(bufferedImage, "PNG", outputFile);

                        extractedImages.add(new ImagemExtraida(outputFile.getAbsolutePath(), posicao));
                        imageCounter++;

                        logger.info("Extraída imagem {}x{} da página {} (posição {}) -> {}",
                                   width, height, pageNum + 1, posicao, filename);
                    } catch (Exception e) {
                        logger.warn("Erro ao extrair imagem da página {}: {}", pageNum + 1, e.getMessage());
                    }
                }

                imageCounter = extrairGraficosVetoriais(document, page, pageNum, posicoes, outputDir, prefix,
                        imageCounter, extractedImages);
            }

            // Sem fallback de renderização de página: o texto vem do PDFBox, e um print da página
            // inteira acabaria anexado a uma questão como se fosse figura dela
            if (extractedImages.isEmpty()) {
                logger.info("Nenhuma imagem útil encontrada no PDF (imagens repetidas/decorativas foram ignoradas)");
            }

            logger.info("Total de imagens extraídas: {}", extractedImages.size());
        }

        return extractedImages;
    }

    /** Posição de um painel dentro da posição da imagem composta de onde ele saiu. */
    private PosicaoImagem subposicao(PosicaoImagem pai, FigurasDeAlternativas.Painel p) {
        if (pai == null) return null;
        double alt = pai.base() - pai.topo(), larg = pai.dir() - pai.esq();
        return new PosicaoImagem(pai.pagina(), pai.topo() + p.topo() * alt, pai.topo() + p.base() * alt,
                pai.esq() + p.esq() * larg, pai.esq() + p.dir() * larg);
    }

    /**
     * Gráficos desenhados em vetor (sem imagem no PDF) dentro de uma moldura retangular: renderiza a página e
     * recorta a moldura, para que cada um vire uma imagem (ex.: as cinco alternativas de gráficos de barras).
     * Molduras que coincidem com uma imagem já extraída são ignoradas.
     *
     * @return o contador de imagens atualizado
     */
    private int extrairGraficosVetoriais(PDDocument document, PDPage page, int pageNum,
                                         Map<String, PosicaoImagem> posicoes, Path outputDir, String prefix,
                                         int imageCounter, List<ImagemExtraida> extraidas) {
        List<java.awt.geom.Rectangle2D.Double> molduras = FigurasDeAlternativas.molduraDeGraficosVetoriais(page);
        if (molduras.isEmpty()) return imageCounter;
        try {
            double larguraPagina = page.getCropBox().getWidth(), alturaPagina = page.getCropBox().getHeight();
            final float escala = 2.0f; // 144 dpi
            BufferedImage renderizada = null;
            for (java.awt.geom.Rectangle2D.Double m : molduras) {
                PosicaoImagem posMoldura = new PosicaoImagem(pageNum + 1, m.y / alturaPagina, (m.y + m.height) / alturaPagina,
                        m.x / larguraPagina, (m.x + m.width) / larguraPagina);
                boolean jaTemImagem = false;
                for (PosicaoImagem existente : posicoes.values()) {
                    if (sobreposicao(posMoldura, existente) > 0.3) {
                        jaTemImagem = true;
                        break;
                    }
                }
                if (jaTemImagem) continue;

                if (renderizada == null) {
                    renderizada = new org.apache.pdfbox.rendering.PDFRenderer(document).renderImage(pageNum, escala);
                }
                int x = (int) Math.max(0, Math.floor((m.x - 2) * escala));
                int y = (int) Math.max(0, Math.floor((m.y - 2) * escala));
                int w = (int) Math.min(renderizada.getWidth() - x, Math.ceil((m.width + 4) * escala));
                int h = (int) Math.min(renderizada.getHeight() - y, Math.ceil((m.height + 4) * escala));
                if (w <= 0 || h <= 0) continue;

                String nome = String.format("%s-page%d-vet%d.png", prefix != null ? prefix : "image", pageNum + 1, imageCounter);
                File arquivo = outputDir.resolve(nome).toFile();
                ImageIO.write(renderizada.getSubimage(x, y, w, h), "PNG", arquivo);
                extraidas.add(new ImagemExtraida(arquivo.getAbsolutePath(), posMoldura));
                imageCounter++;
                logger.info("Gráfico vetorial recortado da página {} ({}x{} pt) -> {}", pageNum + 1,
                        Math.round(m.width), Math.round(m.height), nome);
            }
        } catch (Exception e) {
            logger.warn("Erro ao recortar gráficos vetoriais da página {}: {}", pageNum + 1, e.getMessage());
        }
        return imageCounter;
    }

    /** Área da interseção dividida pela área da menor das duas (0..1). */
    private double sobreposicao(PosicaoImagem a, PosicaoImagem b) {
        double w = Math.min(a.dir(), b.dir()) - Math.max(a.esq(), b.esq());
        double h = Math.min(a.base(), b.base()) - Math.max(a.topo(), b.topo());
        if (w <= 0 || h <= 0) return 0;
        double menor = Math.min((a.dir() - a.esq()) * (a.base() - a.topo()), (b.dir() - b.esq()) * (b.base() - b.topo()));
        return menor <= 0 ? 0 : (w * h) / menor;
    }

    /**
     * Extrai o texto do PDF linha a linha, com a posição de cada linha na página.
     */
    public List<LinhaPdf> extractLines(File pdfFile) throws IOException {
        try (PDDocument document = PDDocument.load(pdfFile)) {
            LineCollector collector = new LineCollector();
            collector.getText(document);
            return collector.finish();
        }
    }

    /**
     * Descobre onde cada imagem da página é desenhada, indexada pelo nome do XObject.
     */
    private Map<String, PosicaoImagem> detectImagePositions(PDPage page, int pagina) {
        Map<String, PosicaoImagem> posicoes = new HashMap<>();
        try {
            float alturaPagina = page.getCropBox().getHeight();
            float baseDaPagina = page.getCropBox().getLowerLeftY();
            float larguraPagina = page.getCropBox().getWidth();
            float esquerdaDaPagina = page.getCropBox().getLowerLeftX();

            ImagePositionEngine engine = new ImagePositionEngine();
            engine.processPage(page);

            engine.desenhos.forEach((nome, retangulo) -> {
                // retangulo = {x, y (borda inferior, coordenada PDF), largura, altura}
                double base = 1.0 - ((retangulo[1] - baseDaPagina) / alturaPagina);
                double topo = 1.0 - ((retangulo[1] + retangulo[3] - baseDaPagina) / alturaPagina);
                double esq = (retangulo[0] - esquerdaDaPagina) / larguraPagina;
                double dir = esq + retangulo[2] / larguraPagina;
                posicoes.put(nome, new PosicaoImagem(pagina, Math.max(0, topo), Math.min(1, base),
                        Math.max(0, esq), Math.min(1, dir)));
            });
        } catch (Exception e) {
            logger.debug("Não foi possível obter a posição das imagens da página {}: {}", pagina, e.getMessage());
        }
        return posicoes;
    }

    /**
     * Retorna os hashes das imagens que aparecem em 2 ou mais páginas distintas.
     */
    private Set<String> detectRepeatedImages(PDDocument document) {
        Map<String, Set<Integer>> paginasPorHash = new HashMap<>();

        for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
            PDResources resources = document.getPage(pageNum).getResources();
            if (resources == null) {
                continue;
            }
            for (COSName name : resources.getXObjectNames()) {
                try {
                    PDXObject xObject = resources.getXObject(name);
                    if (xObject instanceof PDImageXObject) {
                        String hash = hashImage((PDImageXObject) xObject);
                        if (hash != null) {
                            paginasPorHash.computeIfAbsent(hash, k -> new HashSet<>()).add(pageNum);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Erro ao calcular hash de imagem na página {}: {}", pageNum + 1, e.getMessage());
                }
            }
        }

        Set<String> repetidas = new HashSet<>();
        paginasPorHash.forEach((hash, paginas) -> {
            if (paginas.size() >= 2) {
                repetidas.add(hash);
            }
        });

        logger.info("{} imagem(ns) repetida(s) entre páginas serão ignoradas", repetidas.size());
        return repetidas;
    }

    private String hashImage(PDImageXObject image) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(image.getStream().toByteArray());
            md.update((byte) image.getWidth());
            md.update((byte) image.getHeight());
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Percorre o conteúdo da página registrando onde cada imagem (operador Do) é desenhada.
     * Só considera imagens do nível da página, as mesmas que o extrator percorre.
     */
    private static class ImagePositionEngine extends PDFStreamEngine {
        final Map<String, double[]> desenhos = new HashMap<>();

        ImagePositionEngine() {
            addOperator(new Concatenate());
            addOperator(new Restore());
            addOperator(new Save());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new SetMatrix());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.get(0) instanceof COSName) {
                COSName nome = (COSName) operands.get(0);
                PDXObject xObject = getResources().getXObject(nome);
                if (xObject instanceof PDImageXObject && !desenhos.containsKey(nome.getName())) {
                    Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                    // No espaço PDF a imagem ocupa o quadrado unitário transformado pela CTM
                    double largura = Math.abs(ctm.getScalingFactorX());
                    double altura = Math.abs(ctm.getScalingFactorY());
                    desenhos.put(nome.getName(),
                            new double[] {ctm.getTranslateX(), ctm.getTranslateY(), largura, altura});
                }
            } else {
                super.processOperator(operator, operands);
            }
        }
    }

    /**
     * Junta as palavras que o PDFTextStripper entrega uma a uma em linhas, guardando a posição.
     */
    private static class LineCollector extends PDFTextStripper {
        private final List<LinhaPdf> linhas = new ArrayList<>();
        private final StringBuilder atual = new StringBuilder();
        private int paginaAtual = -1;
        private float yAtual = -1;
        private double topoAtual;

        LineCollector() throws IOException {
            setSortByPosition(true);
        }

        @Override
        protected void writeString(String texto, List<TextPosition> posicoes) throws IOException {
            if (posicoes.isEmpty() || texto.isBlank()) {
                return;
            }
            int pagina = getCurrentPageNo();
            float y = posicoes.get(0).getYDirAdj();

            // Mudou de página ou de linha (mais de 2pt de diferença no eixo Y)
            if (pagina != paginaAtual || Math.abs(y - yAtual) > 2f) {
                fecharLinha();
                paginaAtual = pagina;
                yAtual = y;
                topoAtual = y / getCurrentPage().getCropBox().getHeight();
            }
            if (atual.length() > 0) {
                atual.append(' ');
            }
            atual.append(texto);
        }

        private void fecharLinha() {
            if (atual.length() > 0) {
                linhas.add(new LinhaPdf(paginaAtual, topoAtual, atual.toString().trim()));
                atual.setLength(0);
            }
        }

        List<LinhaPdf> finish() {
            fecharLinha();
            return linhas;
        }
    }

    /**
     * Versão simplificada: extrai apenas imagens XObject (mais rápido)
     */
    public List<String> extractEmbeddedImagesOnly(File pdfFile, Path outputDir, String prefix) throws IOException {
        List<String> extractedImages = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            int imageCounter = 0;

            for (int pageNum = 0; pageNum < document.getNumberOfPages(); pageNum++) {
                PDPage page = document.getPage(pageNum);
                PDResources resources = page.getResources();

                for (COSName name : resources.getXObjectNames()) {
                    try {
                        PDXObject xObject = resources.getXObject(name);

                        if (xObject instanceof PDImageXObject) {
                            PDImageXObject image = (PDImageXObject) xObject;

                            // Filtrar imagens pequenas
                            if (image.getWidth() >= 100 && image.getHeight() >= 100) {
                                String filename = String.format("%s-img%d.png",
                                                              prefix != null ? prefix : "image",
                                                              imageCounter);
                                File outputFile = outputDir.resolve(filename).toFile();

                                ImageIO.write(image.getImage(), "PNG", outputFile);
                                extractedImages.add(outputFile.getAbsolutePath());
                                imageCounter++;

                                logger.debug("Extraída imagem embutida: {}", filename);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Erro ao extrair imagem: {}", e.getMessage());
                    }
                }
            }
        }

        return extractedImages;
    }
}
