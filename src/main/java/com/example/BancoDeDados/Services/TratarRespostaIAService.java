package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.ResponseDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TratarRespostaIAService {
    @Autowired
    private GeminiService geminiService;

    private static final Pattern SUG_PATTERN = Pattern.compile("\"sugestao\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
    private static final Pattern PONTOS_PATTERN = Pattern.compile("\"pontosPrincipais\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
    private static final Pattern ITEM_PATTERN = Pattern.compile("\"(.*?)\"", Pattern.DOTALL);

    public List<Questao> processarRespostaIA() {
        List<Questao> questoes = new ArrayList<>();
        String respostaIA = geminiService.gerarResposta(""); // receber prompt externo quando necessário

        Pattern blocoJsonPattern = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);
        Matcher blocoMatcher = blocoJsonPattern.matcher(respostaIA);

        String jsonContent = "";
        if (blocoMatcher.find()) {
            jsonContent = blocoMatcher.group(1).trim();
        } else {
            jsonContent = respostaIA;
        }
        Pattern modeloQuestaoCompleta = Pattern.compile(
                "\\{\\s*\"cabecalho\":\\s*\"([^\"]*)\",\\s*\"enunciado\":\\s*\"([^\"]*)\",\\s*\"alternativas\":\\s*\\[(.*?)\\],\\s*\"gabarito\":\\s*(\\d+)\\s*\\}",
                Pattern.DOTALL
        );

        Matcher matcherQuestaoCompleta = modeloQuestaoCompleta.matcher(jsonContent);

        while (matcherQuestaoCompleta.find()) {
            try {
                Questao questao = new Questao();

                questao.setCabecalho(matcherQuestaoCompleta.group(1).trim());
                questao.setEnunciado(matcherQuestaoCompleta.group(2).trim());

                String alternativasBrutas = matcherQuestaoCompleta.group(3);
                List<String> alternativas = processarAlternativas(alternativasBrutas);
                questao.setAlternativasTexto(alternativas);

                questao.setGabarito(Integer.parseInt(matcherQuestaoCompleta.group(4).trim()));

                questoes.add(questao);
            } catch (Exception e) {
                System.err.println("Erro ao processar questão: " + e.getMessage());
            }
        }

        return questoes;
    }

    private List<String> processarAlternativas(String alternativasBrutas) {
        List<String> alternativas = new ArrayList<>();

        Pattern modeloAlternativas = Pattern.compile("\"([a-eA-E]\\)\\s*[^\"]*)\"");
        Matcher matcherAlternativas = modeloAlternativas.matcher(alternativasBrutas);

        while (matcherAlternativas.find()) {
            alternativas.add(matcherAlternativas.group(1).trim());
        }

        if (alternativas.isEmpty() && alternativasBrutas.contains("\"")) {
            String[] partes = alternativasBrutas.split("\",\\s*\"");
            for (String parte : partes) {
                String alternativa = parte.replace("\"", "").trim();
                if (!alternativa.isEmpty()) {
                    alternativas.add(alternativa);
                }
            }
        }

        return alternativas;
    }

    // Novo método: monta prompt a partir do request DTO e consulta Gemini via GeminiService
    public AnaliseIAResponseDTO analisarListasComGemini(AnaliseIARequestDTO req) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um especialista em análise de desempenho estudantil.\n");
        prompt.append("Analise as top listas da disciplina: ").append(req.getDisciplinaId()).append("\n\n");
        prompt.append("Para cada lista, entregue APENAS:\n");
        prompt.append("- 'sugestao': texto conciso com as principais lacunas de conteúdo observadas;\n");
        prompt.append("- 'pontosPrincipais': lista de 3 a 7 bullets com ações práticas.\n");
        // Evitar caracteres escapados problemáticos em string Java
        prompt.append("IMPORTANTE: Responda apenas um array JSON com os campos sugestao (string) e pontosPrincipais (lista de strings). Não inclua questões, alternativas ou gabaritos.\n\n");

        for (ListaTopDTO lista : req.getListas()) {
            prompt.append("Lista: ").append(lista.getTitulo()).append(" (id=").append(lista.getListaId()).append(")\n");
            for (QuestaoStatsDTO q : lista.getQuestoes()) {
                prompt.append("- QuestaoId=").append(q.getQuestaoId())
                      .append(" | Enunciado: ").append(truncate(q.getEnunciado(), 300))
                      .append(" | Gabarito: ").append(q.getGabarito())
                      .append(" | Acertos: ").append(q.getAcertos())
                      .append(" | Erros: ").append(q.getErros())
                      .append(" | TotalRespondidas: ").append(q.getTotalRespondidas())
                      .append("\n");
            }
            prompt.append("\n");
        }

        String resposta = geminiService.gerarResposta(prompt.toString());

        // Extrair somente o bloco JSON com 'sugestao' e 'pontosPrincipais'
        String json = extrairPrimeiroBlocoSugestao(resposta);
        ParsedIA parsed = parseSugestaoEPontos(json, resposta);
        return new AnaliseIAResponseDTO(parsed.sugestao, parsed.pontos);
    }

    // Extrai o primeiro bloco JSON que contenha as chaves 'sugestao' e 'pontosPrincipais'
    private String extrairPrimeiroBlocoSugestao(String resposta) {
        if (resposta == null) return "[]";
        String texto = resposta.trim();
        // Remover marcadores ```json ... ``` se houver
        texto = texto.replaceAll("```json", "").replaceAll("```", "");
        // Encontrar todos os arrays JSON
        java.util.regex.Pattern arrPat = java.util.regex.Pattern.compile("\\[(.*?)\\]", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher arrMat = arrPat.matcher(texto);
        while (arrMat.find()) {
            String bloco = arrMat.group(0);
            if (bloco.contains("\"sugestao\"") && bloco.contains("\"pontosPrincipais\"")) {
                return bloco;
            }
        }
        // Fallback: se não encontrou arrays, procurar objeto com as chaves
        java.util.regex.Pattern objPat = java.util.regex.Pattern.compile("\\{(.*?)\\}", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher objMat = objPat.matcher(texto);
        while (objMat.find()) {
            String obj = objMat.group(0);
            if (obj.contains("\"sugestao\"") && obj.contains("\"pontosPrincipais\"")) {
                return "[" + obj + "]";
            }
        }
        // Último recurso: retornar texto limpo
        return "[]";
    }

    private String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 3) + "...";
    }

    public List<AnaliseIAListaDTO> analisarPioresListasComGemini(List<ListaTopDTO> topListas) {
        List<AnaliseIAListaDTO> resultados = new ArrayList<>();
        for (ListaTopDTO lista : topListas) {
            StringBuilder prompt = new StringBuilder();
            prompt.append("Você é um especialista em análise pedagógica.\n");
            prompt.append("Analise a lista '").append(lista.getTitulo()).append("' (id=").append(lista.getListaId()).append(") e produza: sugestao (string) e pontosPrincipais (lista com 3-7 itens).\n");
            prompt.append("Não inclua questões, alternativas ou gabaritos.\n\n");
            int totalResp = lista.getQuestoes().stream().mapToInt(QuestaoStatsDTO::getTotalRespondidas).sum();
            int totalErros = lista.getQuestoes().stream().mapToInt(QuestaoStatsDTO::getErros).sum();
            prompt.append("Resumo: totalRespondidas=").append(totalResp).append(", totalErros=").append(totalErros).append("\n");
            String resposta = geminiService.gerarResposta(prompt.toString());
            String json = extrairPrimeiroBlocoSugestao(resposta);
            ParsedIA p = parseSugestaoEPontos(json, resposta);
            resultados.add(new AnaliseIAListaDTO(lista.getListaId(), lista.getTitulo(), p.sugestao, p.pontos));
        }
        return resultados;
    }

    public AnaliseIADisciplinaDTO analisarDisciplinaComGemini(UUID disciplinaId, List<ListaTopDTO> listas) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Você é um especialista pedagógico. Gere uma análise única para a DISCIPLINA inteira (id=")
              .append(disciplinaId).append(") com base no desempenho agregado dessas listas.\n");
        // String corrigida sem escapes inválidos
        prompt.append("Produza APENAS JSON: {\"sugestao\": \"...\", \"pontosPrincipais\": [\"...\"] }\n");
        prompt.append("Contexto (por lista):\n");
        for (ListaTopDTO l : listas) {
            int totalResp = l.getQuestoes().stream().mapToInt(QuestaoStatsDTO::getTotalRespondidas).sum();
            int totalErros = l.getQuestoes().stream().mapToInt(QuestaoStatsDTO::getErros).sum();
            prompt.append("- ").append(l.getTitulo()).append(" id=").append(l.getListaId())
                  .append(" respostas=").append(totalResp)
                  .append(" erros=").append(totalErros)
                  .append("\n");
        }
        prompt.append("Foque em lacunas comuns e ações para melhorar a disciplina como um todo.\n");
        String resposta = geminiService.gerarResposta(prompt.toString());
        String json = extrairPrimeiroBlocoSugestao(resposta);
        ParsedIA p = parseSugestaoEPontos(json, resposta);
        return new AnaliseIADisciplinaDTO(disciplinaId, p.sugestao, p.pontos);
    }

    private ParsedIA parseSugestaoEPontos(String json, String fallbackTexto) {
        String sugestao = null; List<String> pontos = new ArrayList<>();
        try {
            Matcher mSug = SUG_PATTERN.matcher(json);
            if (mSug.find()) sugestao = mSug.group(1).trim();
            Matcher mP = PONTOS_PATTERN.matcher(json);
            if (mP.find()) {
                String inner = mP.group(1);
                Matcher mItem = ITEM_PATTERN.matcher(inner);
                while (mItem.find()) {
                    String item = mItem.group(1).trim();
                    if (!item.isBlank()) pontos.add(item);
                }
            }
        } catch (Exception ignored) {}
        if (sugestao == null) sugestao = fallbackTexto != null ? fallbackTexto : "";
        return new ParsedIA(sugestao, pontos);
    }

    private static class ParsedIA {
        String sugestao; List<String> pontos;
        ParsedIA(String s, List<String> p){ this.sugestao = s; this.pontos = p; }
    }
}
