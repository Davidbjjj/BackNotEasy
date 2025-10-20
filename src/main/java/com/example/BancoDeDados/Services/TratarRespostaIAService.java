package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TratarRespostaIAService {
    @Autowired
    IAService IAService;

    public List<Questao> processarRespostaIA() {
        List<Questao> questoes = new ArrayList<>();
        String respostaIA = IAService.getRespostaDoGemini();

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
                questao.setAlternativas(alternativas);

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
}