package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Services.NotaListaService;
import com.example.BancoDeDados.Services.RespostaEstudantesService;
import com.example.BancoDeDados.Services.ListaService;
import com.example.BancoDeDados.Model.ListaEstudanteNota;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class RespostaEstudantesController {

    private final RespostaEstudantesService respostaEstudantesService;
    private final NotaListaService notaListaService;
    private final ListaService listaService;

    public RespostaEstudantesController(RespostaEstudantesService respostaEstudantesService,
                                        NotaListaService notaListaService,
                                        ListaService listaService) {
        this.respostaEstudantesService = respostaEstudantesService;
        this.notaListaService = notaListaService;
        this.listaService = listaService;
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
    // Endpoint com estatísticas gerais
    @GetMapping("/listas/{listaId}/respostas-com-estatisticas")
    public ResponseEntity<RespostasListaComEstatisticasDTO> getRespostasPorListaComEstatisticas(@PathVariable UUID listaId) {
        RespostasListaComEstatisticasDTO respostas = notaListaService.buscarRespostasPorListaComEstatisticas(listaId);
        return ResponseEntity.ok(respostas);
    }

    // Endpoint com nota específica do estudante
    @GetMapping("/listas/{listaId}/estudantes/{estudanteId}/respostas-com-nota")
    public ResponseEntity<RespostasListaComNotaDTO> getRespostasPorListaComNotaPorEstudante(
            @PathVariable UUID listaId,
            @PathVariable UUID estudanteId) {
        RespostasListaComNotaDTO respostas = notaListaService.buscarRespostasPorListaComNotaPorEstudante(listaId, estudanteId);
        return ResponseEntity.ok(respostas);


    }

    // NOVO ENDPOINT: Visão do aluno para uma lista
    // Se o estudante já tiver nota (lista respondida) retorna { respondida: true, nota, porcentagemAcertos, questoesRespondidas, questoesCorretas, totalQuestoes }
    // Caso contrário retorna { respondida: false, questoes: [...] }
    @GetMapping("/listas/{listaId}/estudantes/{estudanteId}/visao")
    public ResponseEntity<?> visaoAluno(@PathVariable UUID listaId, @PathVariable UUID estudanteId) {
        try {
            ListaEstudanteNota nota = notaListaService.buscarNotaEstudante(listaId, estudanteId); // garante registro
            boolean todasRespondidas = nota.getQuestoesRespondidas() != null && nota.getTotalQuestoes() != null && nota.getQuestoesRespondidas().equals(nota.getTotalQuestoes());
            boolean respondida = todasRespondidas && nota.isFinalizada();
            if (respondida) {
                Map<String, Object> out = new HashMap<>();
                out.put("respondida", true);
                out.put("nota", nota.getNota());
                out.put("porcentagemAcertos", nota.getPorcentagemAcertos());
                out.put("questoesRespondidas", nota.getQuestoesRespondidas());
                out.put("questoesCorretas", nota.getQuestoesCorretas());
                out.put("totalQuestoes", nota.getTotalQuestoes());
                out.put("finalizada", true);
                return ResponseEntity.ok(out);
            }
            // Não finalizada ou faltam questões -> retorna lista e progresso
            var listaCompleta = listaService.buscarListaCompletaComImagens(listaId);
            Map<String, Object> out = new HashMap<>();
            out.put("respondida", false);
            out.put("finalizada", nota.isFinalizada());
            out.put("progresso", Map.of(
                    "questoesRespondidas", nota.getQuestoesRespondidas(),
                    "totalQuestoes", nota.getTotalQuestoes()
            ));
            out.putAll(listaCompleta);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/listas/{listaId}/estudantes/{estudanteId}/finalizar")
    public ResponseEntity<?> finalizarLista(@PathVariable UUID listaId, @PathVariable UUID estudanteId) {
        try {
            ListaEstudanteNota nota = notaListaService.finalizarListaAluno(listaId, estudanteId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("respondida", true);
            resp.put("finalizada", true);
            resp.put("nota", nota.getNota());
            resp.put("porcentagemAcertos", nota.getPorcentagemAcertos());
            resp.put("questoesRespondidas", nota.getQuestoesRespondidas());
            resp.put("questoesCorretas", nota.getQuestoesCorretas());
            resp.put("totalQuestoes", nota.getTotalQuestoes());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
