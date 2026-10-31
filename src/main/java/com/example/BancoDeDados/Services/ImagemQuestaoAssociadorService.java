package com.example.BancoDeDados.Services;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para associar imagens às questões corretas baseado no contexto do texto OCR
 */
@Service
public class ImagemQuestaoAssociadorService {

    private static final Logger logger = LoggerFactory.getLogger(ImagemQuestaoAssociadorService.class);

    // Padrões para identificar questões no texto OCR
    private static final Pattern QUESTAO_PATTERN = Pattern.compile(
        "(?:Exercício|Questão|Quest[ãa]o|Pergunta|Q\\.?)\\s*(?:n[ºo°]?\\s*)?([\\d]+)",
        Pattern.CASE_INSENSITIVE
    );

    // Uma questão não se estende por mais que isso (em páginas) a partir da linha em que começa
    private static final double LIMITE_PAGINAS_POR_QUESTAO = 1.5;

    /**
     * Associa imagens às questões pela posição no PDF: a imagem pertence à última questão
     * que começou antes dela, desde que a questão ainda não tenha terminado.
     * Uma questão termina no início da próxima ou numa linha "Gabarito" (fim do comentário).
     * Imagens fora de qualquer questão (capa, entre seções, depois do gabarito) não são associadas.
     *
     * @param questoes questões extraídas, na ordem em que aparecem no PDF
     * @param linhas linhas do PDF com posição
     * @param posicoes posição de cada imagem (mesma ordem da lista de imagens; null se desconhecida)
     * @return Map onde chave é o número da questão (1-based) e valor é a lista de índices de imagens
     */
    public Map<Integer, List<Integer>> agruparImagensPorPosicao(
            List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> questoes,
            List<com.example.BancoDeDados.service.LinhaPdf> linhas,
            List<com.example.BancoDeDados.service.PosicaoImagem> posicoes) {

        Map<Integer, List<Integer>> grupos = new HashMap<>();
        for (int i = 1; i <= questoes.size(); i++) {
            grupos.put(i, new ArrayList<>());
        }

        // Onde cada questão começa (chave = pagina + proporção da página); NaN quando não localizada
        double[] inicios = localizarInicioDasQuestoes(questoes, linhas);

        // Linhas "Gabarito" marcam o fim do bloco de uma questão comentada
        List<Double> fimsDeBloco = new ArrayList<>();
        for (com.example.BancoDeDados.service.LinhaPdf linha : linhas) {
            if (normalizar(linha.texto()).startsWith("gabarito")) {
                fimsDeBloco.add(chave(linha.pagina(), linha.topo()));
            }
        }

        for (int i = 0; i < posicoes.size(); i++) {
            com.example.BancoDeDados.service.PosicaoImagem posicao = posicoes.get(i);
            if (posicao == null) {
                continue;
            }
            double chaveImagem = chave(posicao.pagina(), posicao.centro());

            // Última questão localizada que começou antes da imagem
            int questaoIdx = -1;
            for (int q = 0; q < inicios.length; q++) {
                if (!Double.isNaN(inicios[q]) && inicios[q] <= chaveImagem
                        && (questaoIdx < 0 || inicios[q] >= inicios[questaoIdx])) {
                    questaoIdx = q;
                }
            }
            if (questaoIdx < 0) {
                logger.debug("Imagem {} (página {}) antes da primeira questão: ignorada", i, posicao.pagina());
                continue;
            }

            double inicio = inicios[questaoIdx];
            boolean depoisDoFim = fimsDeBloco.stream().anyMatch(fim -> fim > inicio && fim < chaveImagem);
            if (depoisDoFim || chaveImagem - inicio > LIMITE_PAGINAS_POR_QUESTAO) {
                logger.debug("Imagem {} (página {}) fora do bloco da questão {}: ignorada",
                        i, posicao.pagina(), questaoIdx + 1);
                continue;
            }

            grupos.get(questaoIdx + 1).add(i);
            logger.info("Imagem {} (página {}) associada à questão {} por posição", i, posicao.pagina(), questaoIdx + 1);
        }

        return grupos;
    }

    /**
     * Localiza, na ordem do documento, a linha em que cada questão começa. Compara o início do
     * cabeçalho+enunciado (sem acentos/pontuação) com o início de cada linha do PDF, sempre
     * procurando depois da questão anterior (evita casar com o sumário ou com o texto repetido).
     */
    private double[] localizarInicioDasQuestoes(
            List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> questoes,
            List<com.example.BancoDeDados.service.LinhaPdf> linhas) {

        double[] inicios = new double[questoes.size()];
        Arrays.fill(inicios, Double.NaN);
        int proximaLinha = 0;

        for (int q = 0; q < questoes.size(); q++) {
            var questao = questoes.get(q);
            String base = (questao.getContexto() == null ? "" : questao.getContexto()) + " "
                    + (questao.getEnunciado() == null ? "" : questao.getEnunciado());
            String chaveTexto = normalizar(base);
            chaveTexto = chaveTexto.substring(0, Math.min(18, chaveTexto.length()));
            if (chaveTexto.length() < 6) {
                continue;
            }

            for (int l = proximaLinha; l < linhas.size(); l++) {
                if (normalizar(linhas.get(l).texto()).startsWith(chaveTexto)) {
                    inicios[q] = chave(linhas.get(l).pagina(), linhas.get(l).topo());
                    proximaLinha = l + 1;
                    break;
                }
            }
            if (Double.isNaN(inicios[q])) {
                logger.warn("Início da questão {} não localizado no PDF; suas imagens não serão associadas", q + 1);
            }
        }
        return inicios;
    }

    /** Posição linear no documento: parte inteira = página, parte fracionária = posição na página. */
    private double chave(int pagina, double proporcaoNaPagina) {
        return pagina + Math.min(Math.max(proporcaoNaPagina, 0), 0.999);
    }

    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /**
     * Associa cada imagem à questão correta baseado no número da questão encontrado no OCR
     * (usado apenas quando não há posição no PDF, por exemplo arquivos que não são PDF)
     *
     * @param textosOcr Textos extraídos de cada imagem (ordenados por página)
     * @param totalQuestoes Número total de questões extraídas
     * @return Map onde chave é índice da imagem e valor é número da questão (1-based)
     */
    public Map<Integer, Integer> associarImagensAQuestoes(List<String> textosOcr, int totalQuestoes) {
        Map<Integer, Integer> associacoes = new HashMap<>();

        for (int i = 0; i < textosOcr.size(); i++) {
            String textoOcr = textosOcr.get(i);

            // Tentar identificar número da questão no texto OCR
            Integer numeroQuestao = extrairNumeroQuestao(textoOcr);

            if (numeroQuestao != null && numeroQuestao <= totalQuestoes) {
                associacoes.put(i, numeroQuestao);
                logger.debug("Imagem {} associada à Questão {}", i, numeroQuestao);
            } else {
                // Se não encontrou número, usar heurística de distribuição
                int questaoEstimada = estimarQuestaoParaImagem(i, textosOcr.size(), totalQuestoes);
                associacoes.put(i, questaoEstimada);
                logger.debug("Imagem {} associada à Questão {} (estimativa)", i, questaoEstimada);
            }
        }

        logger.info("Associadas {} imagens a {} questões", associacoes.size(), totalQuestoes);
        return associacoes;
    }

    /**
     * Extrai o número da questão do texto OCR
     */
    private Integer extrairNumeroQuestao(String textoOcr) {
        if (textoOcr == null || textoOcr.isBlank()) {
            return null;
        }

        Matcher matcher = QUESTAO_PATTERN.matcher(textoOcr);

        // Pegar a primeira ocorrência (geralmente a mais relevante)
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                logger.warn("Erro ao parsear número da questão: {}", matcher.group(1));
            }
        }

        return null;
    }

    /**
     * Estima qual questão uma imagem pertence baseado na posição da imagem
     * Assume distribuição uniforme de imagens por questões
     */
    private int estimarQuestaoParaImagem(int indiceImagem, int totalImagens, int totalQuestoes) {
        // Distribuição simples: divide imagens igualmente entre questões
        double imagensPorQuestao = (double) totalImagens / totalQuestoes;
        int questaoEstimada = (int) Math.ceil((indiceImagem + 1) / imagensPorQuestao);

        // Garantir que está no range válido
        return Math.max(1, Math.min(questaoEstimada, totalQuestoes));
    }

    /**
     * Agrupa índices de imagens por número de questão
     *
     * @param textosOcr Textos OCR das imagens
     * @param totalQuestoes Total de questões
     * @return Map onde chave é número da questão e valor é lista de índices de imagens
     */
    public Map<Integer, List<Integer>> agruparImagensPorQuestao(List<String> textosOcr, int totalQuestoes) {
        Map<Integer, Integer> associacoes = associarImagensAQuestoes(textosOcr, totalQuestoes);
        Map<Integer, List<Integer>> grupos = new HashMap<>();

        // Inicializar listas para cada questão
        for (int i = 1; i <= totalQuestoes; i++) {
            grupos.put(i, new ArrayList<>());
        }

        // Agrupar índices de imagens por questão
        for (Map.Entry<Integer, Integer> entry : associacoes.entrySet()) {
            int indiceImagem = entry.getKey();
            int numeroQuestao = entry.getValue();
            grupos.get(numeroQuestao).add(indiceImagem);
        }

        // Log de debug
        grupos.forEach((questao, imagens) -> {
            if (!imagens.isEmpty()) {
                logger.info("Questão {} tem {} imagem(ns): {}", questao, imagens.size(), imagens);
            }
        });

        return grupos;
    }

    /**
     * Verifica se uma imagem contém referência a uma questão específica
     */
    public boolean imagemPertenceAQuestao(String textoOcr, int numeroQuestao) {
        Integer numeroEncontrado = extrairNumeroQuestao(textoOcr);
        return numeroEncontrado != null && numeroEncontrado.equals(numeroQuestao);
    }

    /**
     * Calcula score de relevância de uma imagem para uma questão baseado no texto
     * Útil para casos ambíguos
     */
    public double calcularRelevancia(String textoOcr, String enunciadoQuestao) {
        if (textoOcr == null || textoOcr.isBlank() || enunciadoQuestao == null) {
            return 0.0;
        }

        // Normalizar textos
        String ocrNorm = textoOcr.toLowerCase().trim();
        String enunciadoNorm = enunciadoQuestao.toLowerCase().trim();

        // Extrair palavras-chave do enunciado (> 4 caracteres)
        Set<String> palavrasChave = new HashSet<>();
        for (String palavra : enunciadoNorm.split("\\s+")) {
            if (palavra.length() > 4) {
                palavrasChave.add(palavra);
            }
        }

        // Contar quantas palavras-chave aparecem no OCR
        int matches = 0;
        for (String palavra : palavrasChave) {
            if (ocrNorm.contains(palavra)) {
                matches++;
            }
        }

        // Score normalizado
        return palavrasChave.isEmpty() ? 0.0 : (double) matches / palavrasChave.size();
    }
}

