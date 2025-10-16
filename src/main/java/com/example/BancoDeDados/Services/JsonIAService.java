package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JsonIAService {

    @Autowired
    private TratarRespostaIAService tratarRespostaIA;

    public String gerarJsonRespostaIA() {
        List<Questao> questoes = tratarRespostaIA.processarRespostaIA();

        // Cria o ObjectMapper e habilita a formatação bonita (pretty print)
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);

        try {
            return mapper.writeValueAsString(questoes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erro ao converter lista de questões para JSON", e);
        }
    }

    public String exibirQuestoesDoJson(String json) {
        System.out.println("Questões em formato JSON formatado: \n" + json);
        return json;
    }
}
