package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Services.JsonService;
import com.example.BancoDeDados.Services.PDFService;
import com.example.BancoDeDados.Services.QuestaoService;
import com.example.BancoDeDados.Services.TratarTextoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/pdf")
public class PDFController {

    @Autowired
    private PDFService PDFService;

    @Autowired
    private TratarTextoService tratarTexto;

    @Autowired
    private JsonService json;

    @Autowired
    private QuestaoService questaoService;


    @PostMapping("/processar-salvar")
    public String processarESalvarPdf() throws IOException {
        List<Questao> questoes = tratarTexto.pegarQuestoes();
        questaoService.salvarQuestoes(questoes);
        return json.exibirQuestoesDoJson(json.jsonDoCorpo());
    }
}