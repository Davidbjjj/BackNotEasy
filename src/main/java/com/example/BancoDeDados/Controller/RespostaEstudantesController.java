package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.EnviarMultiplasRespostasDTO;
import com.example.BancoDeDados.ResponseDTO.EnviarRespostaDTO;
import com.example.BancoDeDados.ResponseDTO.RespostaEstudanteDTO;
import com.example.BancoDeDados.ResponseDTO.RespostasListaDTO;
import com.example.BancoDeDados.Services.RespostaEstudantesService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RespostaEstudantesController {

    private final RespostaEstudantesService respostaEstudantesService;

    public RespostaEstudantesController(RespostaEstudantesService respostaEstudantesService) {
        this.respostaEstudantesService = respostaEstudantesService;
    }

    @GetMapping("/respostas/buscar")
    public RespostaEstudanteDTO buscarRespostaPorQuestaoEEstudante(
            @RequestParam Integer questaoId,
            @RequestParam UUID estudanteId) {
        return respostaEstudantesService.buscarRespostaPorQuestaoEEstudante(questaoId, estudanteId);
    }
    @PostMapping("/enviaresposta")
    public ResponseEntity<String> enviarResposta(@RequestBody EnviarRespostaDTO enviarRespostaDTO) {
        respostaEstudantesService.salvarResposta(enviarRespostaDTO);
        return ResponseEntity.ok("Resposta enviada com sucesso.");
    }
    @PostMapping("/respostas/multiplas")
    public ResponseEntity<String> salvarMultiplasRespostas(@RequestBody EnviarMultiplasRespostasDTO multiplasRespostasDTO) {
        try {
            respostaEstudantesService.salvarMultiplasRespostasOtimizado(multiplasRespostasDTO);
            return ResponseEntity.ok("Respostas salvas com sucesso!");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao salvar respostas: " + e.getMessage());
        }
    }
    @GetMapping("/questoes-respondidas")
    public ResponseEntity<List<Integer>> buscarQuestoesRespondidasPorListaEEstudante(
            @RequestParam UUID listaId,
            @RequestParam UUID estudanteId) {
        List<Integer> questoesRespondidas = respostaEstudantesService.buscarQuestoesRespondidasPorListaEEstudante(listaId, estudanteId);
        return ResponseEntity.ok(questoesRespondidas);
    }


    @GetMapping("/listas/{listaId}/respostas")
    public ResponseEntity<RespostasListaDTO> getRespostasPorLista(@PathVariable UUID listaId) {
        RespostasListaDTO respostas = respostaEstudantesService.buscarRespostasPorLista(listaId);
        return ResponseEntity.ok(respostas);
    }




}
