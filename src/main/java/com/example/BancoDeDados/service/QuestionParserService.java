package com.example.BancoDeDados.service;

import com.example.BancoDeDados.dto.QuestaoExtraidaDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serviço para parsing e extração estruturada de questões de texto OCR
 */
@Service
public class QuestionParserService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionParserService.class);

    // Padrões regex para identificação de questões
    private static final Pattern QUESTAO_PATTERN = Pattern.compile(
        "(?:Exercício|Questão|Quest[ãa]o|Pergunta|Q\\.?)\\s*(?:n[ºo°]?\\s*)?([\\d]+)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ALTERNATIVA_PATTERN = Pattern.compile(
        "^\\s*([a-eA-E])\\s*[\\)\\.]\\s*(.+?)(?=\\n\\s*[a-eA-E]\\s*[\\)\\.]|\\n\\n|$)",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    private static final Pattern GABARITO_PATTERN = Pattern.compile(
        "(?:Gabarito|Resposta|Resp\\.?)\\s*:?\\s*([a-eA-E])",
        Pattern.CASE_INSENSITIVE
    );

    private final TextCleanerService textCleaner;

    public QuestionParserService(TextCleanerService textCleaner) {
        this.textCleaner = textCleaner;
    }

    /**
     * Extrai todas as questões de um texto
     */
    public List<QuestaoExtraidaDTO> parseQuestoes(String texto) {
        if (texto == null || texto.isBlank()) {
            logger.warn("Texto vazio ou nulo fornecido para parsing");
            return new ArrayList<>();
        }

        // Limpar texto primeiro
        String textoLimpo = textCleaner.fullClean(texto);
        textoLimpo = textCleaner.removeHeadersFooters(textoLimpo);

        List<QuestaoExtraidaDTO> questoes = new ArrayList<>();

        // Dividir texto em blocos de questões
        List<BlocoQuestao> blocos = dividirEmBlocos(textoLimpo);

        for (BlocoQuestao bloco : blocos) {
            try {
                QuestaoExtraidaDTO questao = parseQuestao(bloco);
                if (questao != null && isQuestaoValida(questao)) {
                    questoes.add(questao);
                }
            } catch (Exception e) {
                logger.error("Erro ao fazer parse de questão {}: {}", bloco.numero, e.getMessage());
            }
        }

        logger.info("Extraídas {} questões do texto", questoes.size());
        return questoes;
    }

    /**
     * Divide texto em blocos por questão
     */
    private List<BlocoQuestao> dividirEmBlocos(String texto) {
        List<BlocoQuestao> blocos = new ArrayList<>();
        Matcher matcher = QUESTAO_PATTERN.matcher(texto);

        int lastEnd = 0;
        Integer lastNumero = null;

        while (matcher.find()) {
            // Salvar bloco anterior se existir
            if (lastNumero != null) {
                String conteudo = texto.substring(lastEnd, matcher.start()).trim();
                blocos.add(new BlocoQuestao(lastNumero, conteudo));
            }

            lastNumero = Integer.parseInt(matcher.group(1));
            lastEnd = matcher.start();
        }

        // Adicionar último bloco
        if (lastNumero != null) {
            String conteudo = texto.substring(lastEnd).trim();
            blocos.add(new BlocoQuestao(lastNumero, conteudo));
        }

        return blocos;
    }

    /**
     * Faz parse de um bloco individual de questão
     */
    private QuestaoExtraidaDTO parseQuestao(BlocoQuestao bloco) {
        QuestaoExtraidaDTO questao = new QuestaoExtraidaDTO();
        questao.setNumero(bloco.numero);

        String texto = bloco.conteudo;

        // Extrair gabarito se presente
        Matcher gabaritoMatcher = GABARITO_PATTERN.matcher(texto);
        if (gabaritoMatcher.find()) {
            questao.setGabarito(gabaritoMatcher.group(1).toLowerCase());
            // Remover linha do gabarito do texto
            texto = texto.substring(0, gabaritoMatcher.start()) +
                    texto.substring(gabaritoMatcher.end());
        }

        // Extrair alternativas
        Matcher altMatcher = ALTERNATIVA_PATTERN.matcher(texto);
        int primeiraAlternativaPos = -1;

        while (altMatcher.find()) {
            if (primeiraAlternativaPos == -1) {
                primeiraAlternativaPos = altMatcher.start();
            }
            String letra = altMatcher.group(1).toLowerCase();
            String textoAlt = altMatcher.group(2).trim();
            questao.addAlternativa(letra, textoAlt);
        }

        // Enunciado é tudo antes da primeira alternativa
        if (primeiraAlternativaPos > 0) {
            String enunciado = texto.substring(0, primeiraAlternativaPos).trim();
            // Remover cabeçalho "Exercício X"
            enunciado = QUESTAO_PATTERN.matcher(enunciado).replaceFirst("").trim();
            questao.setEnunciado(enunciado);
        } else {
            // Sem alternativas identificadas - todo o texto é enunciado
            questao.setEnunciado(texto.trim());
        }

        // Calcular confiança baseado na completude
        questao.setConfianca(calcularConfianca(questao));

        return questao;
    }

    /**
     * Valida se questão extraída é válida
     */
    private boolean isQuestaoValida(QuestaoExtraidaDTO questao) {
        if (questao.getEnunciado() == null || questao.getEnunciado().length() < 10) {
            logger.debug("Questão {} rejeitada: enunciado muito curto", questao.getNumero());
            return false;
        }

        // Questão múltipla escolha deve ter pelo menos 2 alternativas
        if (questao.getAlternativas().isEmpty()) {
            logger.debug("Questão {} sem alternativas (pode ser discursiva)", questao.getNumero());
            // Ainda é válida, pode ser questão aberta
            return true;
        }

        if (questao.getAlternativas().size() < 2) {
            logger.debug("Questão {} rejeitada: apenas {} alternativa",
                        questao.getNumero(), questao.getAlternativas().size());
            return false;
        }

        return true;
    }

    /**
     * Calcula nível de confiança da extração (0-1)
     */
    private double calcularConfianca(QuestaoExtraidaDTO questao) {
        double confianca = 0.5; // Base

        // Tem enunciado razoável?
        if (questao.getEnunciado() != null && questao.getEnunciado().length() > 20) {
            confianca += 0.2;
        }

        // Tem alternativas?
        int numAlternativas = questao.getAlternativas().size();
        if (numAlternativas >= 4) {
            confianca += 0.2;
        } else if (numAlternativas >= 2) {
            confianca += 0.1;
        }

        // Alternativas são sequenciais? (a, b, c, d, e)
        if (isAlternativasSequenciais(questao)) {
            confianca += 0.1;
        }

        return Math.min(1.0, confianca);
    }

    /**
     * Verifica se alternativas seguem sequência correta
     */
    private boolean isAlternativasSequenciais(QuestaoExtraidaDTO questao) {
        if (questao.getAlternativas().size() < 2) return false;

        char expected = 'a';
        for (QuestaoExtraidaDTO.AlternativaDTO alt : questao.getAlternativas()) {
            if (alt.getLetra().charAt(0) != expected) {
                return false;
            }
            expected++;
        }
        return true;
    }

    /**
     * Classe auxiliar para representar bloco de questão
     */
    private static class BlocoQuestao {
        final Integer numero;
        final String conteudo;

        BlocoQuestao(Integer numero, String conteudo) {
            this.numero = numero;
            this.conteudo = conteudo;
        }
    }
}

