package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.ResponseDTO.QuestaoDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.QuestaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/questao")
public class QuestaoController {

    @Autowired
    private QuestaoService questaoService;

    @PostMapping("/criar")
    public Questao criarQuestao(
            @RequestParam String cabecalho,
            @RequestParam String enunciado,
            @RequestParam List<String> alternativas,
            @RequestParam Integer gabarito) {
        return questaoService.criarQuestao(cabecalho, enunciado, alternativas, gabarito);
    }

    @GetMapping("/{id}")
    public QuestaoResponseDTO buscarQuestao(@PathVariable int id) {
        QuestaoResponseDTO dto = questaoService.buscarQuestaoPorIdComGabaritoDTO(id);
        if (dto != null && !CallerContext.isStaff()) dto.setGabarito(null); // gabarito só para staff
        return dto;
    }

    @GetMapping("/questoes")
    public List<QuestaoDTO> listarQuestoes() {
        List<QuestaoDTO> questoes = questaoService.listarQuestoes();
        if (!CallerContext.isStaff()) questoes.forEach(q -> q.setGabarito(null)); // gabarito só para staff
        return questoes;
    }

    @DeleteMapping("/deletar/{id}")
    public void deletarQuestao(@PathVariable int id) {
        questaoService.deletarQuestao(id);
    }
}
