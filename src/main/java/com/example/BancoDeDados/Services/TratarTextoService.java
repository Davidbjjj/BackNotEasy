package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TratarTextoService {

    @Autowired
    private PDFService texto; // Injeção do ServicePDF

    public List<Questao> pegarQuestoes() throws IOException {
        List<Questao> questoes = new ArrayList<>();

        Pattern modeloQuestaoCompleta = Pattern.compile(
                "(\\d+\\.\\s\\([^\\)]+\\))\\s+(.*?)(?=(?:\\d+\\.\\s\\([^\\)]+\\))|$)",
                Pattern.DOTALL
        );
        Matcher matcherQuestaoCompleta = modeloQuestaoCompleta.matcher(texto.TextoExtraido());

        while (matcherQuestaoCompleta.find()) {
            Questao questao = new Questao();
            questao.setCabecalho(matcherQuestaoCompleta.group(1).trim());

            String enunciadoCompleto = matcherQuestaoCompleta.group(2).trim();
            String enunciado = removerAlternativas(enunciadoCompleto);
            questao.setEnunciado(enunciado);

            List<String> alternativas = pegarAlternativas(enunciadoCompleto);
            questao.setAlternativasTexto(alternativas);
            questoes.add(questao);
        }

        return questoes;
    }

    private String removerAlternativas(String texto) {
        Pattern modeloAlternativas = Pattern.compile(
                "(?:\\b[A-Ea-e]\\)\\s*|\\(A\\)|\\(B\\)|\\(C\\)|\\(D\\)|\\(E\\)\\s*)[^\\n]*",
                Pattern.DOTALL
        );
        Matcher matcherAlternativas = modeloAlternativas.matcher(texto);

        return matcherAlternativas.replaceAll("").trim();
    }

    public List<String> pegarAlternativas(String texto) {
        List<String> alternativas = new ArrayList<>();

        Pattern modeloAlternativas = Pattern.compile(
                "(?:\\b[A-Ea-e]\\)\\s*[^\\n]*|\\(A\\)[^\\n]*|\\(B\\)[^\\n]*|\\(C\\)[^\\n]*|\\(D\\)[^\\n]*|\\(E\\)[^\\n]*)",
                Pattern.DOTALL
        );
        Matcher matcherAlternativas = modeloAlternativas.matcher(texto);

        while (matcherAlternativas.find()) {
            String alternativa = matcherAlternativas.group().trim();
            if (!alternativa.isEmpty()) {
                alternativas.add(alternativa);
            }
        }

        return alternativas;
    }
}
