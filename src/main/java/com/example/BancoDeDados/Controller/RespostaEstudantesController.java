package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Security.CallerContext;
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

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.BancoDeDados.Repositores.ListaRepository listaRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private com.example.BancoDeDados.Services.QuestaoOtimizadoService questaoOtimizadoService;

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

    /** FE-205: regras de aplicação que o aluno precisa ver (janela, tempo limite, tentativas). */
    private Map<String, Object> configParaAluno(UUID listaId, ListaEstudanteNota nota) {
        var lista = listaRepository.findById(listaId).orElse(null);
        Map<String, Object> cfg = new HashMap<>();
        if (lista == null) return cfg;
        int max = NotaListaService.maxTentativas(lista);
        int usadas = nota.getTentativas() == null ? 0 : nota.getTentativas();
        if (nota.isFinalizada() && usadas == 0) usadas = 1; // finalizadas antes do controle de tentativas
        java.time.Instant agora = java.time.Instant.now();
        boolean fechada = lista.getFechaEm() != null && agora.isAfter(lista.getFechaEm());
        Long prazoFinal = null;
        if (lista.getTempoLimiteMinutos() != null && nota.getIniciadaEm() != null && !nota.isFinalizada()) {
            prazoFinal = nota.getIniciadaEm().plusSeconds(lista.getTempoLimiteMinutos() * 60L).toEpochMilli();
        }
        if (lista.getFechaEm() != null && !nota.isFinalizada()) {
            long f = lista.getFechaEm().toEpochMilli();
            prazoFinal = prazoFinal == null ? f : Math.min(prazoFinal, f);
        }
        cfg.put("abreEm", lista.getAbreEm());
        cfg.put("fechaEm", lista.getFechaEm());
        cfg.put("aberta", (lista.getAbreEm() == null || !agora.isBefore(lista.getAbreEm())) && !fechada);
        cfg.put("tempoLimiteMinutos", lista.getTempoLimiteMinutos());
        cfg.put("prazoFinal", prazoFinal);
        cfg.put("maxTentativas", max);
        cfg.put("tentativasUsadas", usadas);
        cfg.put("regraNota", lista.getRegraNota() == null ? "MAIOR" : lista.getRegraNota());
        cfg.put("podeNovaTentativa", nota.isFinalizada() && usadas < max && !fechada);
        cfg.put("pendenteCorrecao", notaListaService.contarPendentesCorrecao(listaId, nota.getEstudante().getId()));
        return cfg;
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
        CallerContext.exigirEstudanteOuStaff(estudanteId); // fora do try: 401/403 não vira 400
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
                out.put("config", configParaAluno(listaId, nota));
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
            var listaEnt = listaRepository.findById(listaId).orElse(null);
            if (listaEnt != null && listaCompleta.get("questoes") instanceof List<?> qs) {
                @SuppressWarnings("unchecked")
                List<com.example.BancoDeDados.dto.QuestaoComAlternativasDTO> questoes =
                        (List<com.example.BancoDeDados.dto.QuestaoComAlternativasDTO>) qs;
                out.put("questoes", questaoOtimizadoService.embaralharParaAluno(listaId, estudanteId, questoes,
                        listaEnt.isEmbaralharQuestoes(), listaEnt.isEmbaralharAlternativas()));
            }
            out.put("config", configParaAluno(listaId, nota));
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // FE-102: revisão com gabarito. 403 se não finalizou ou gabarito não liberado (ResponseStatusException).
    @GetMapping("/listas/{listaId}/estudantes/{estudanteId}/revisao")
    public ResponseEntity<RevisaoListaDTO> revisao(@PathVariable UUID listaId, @PathVariable UUID estudanteId) {
        CallerContext.exigirEstudanteOuStaff(estudanteId);
        return ResponseEntity.ok(notaListaService.buscarRevisao(listaId, estudanteId));
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
            resp.put("pendenteCorrecao", notaListaService.contarPendentesCorrecao(listaId, estudanteId));
            resp.put("config", configParaAluno(listaId, nota));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** FE-205: apaga as respostas e reabre a lista para o aluno, se ainda houver tentativas e prazo. */
    @PostMapping("/listas/{listaId}/estudantes/{estudanteId}/nova-tentativa")
    public ResponseEntity<?> novaTentativa(@PathVariable UUID listaId, @PathVariable UUID estudanteId) {
        CallerContext.exigirEstudanteOuStaff(estudanteId);
        try {
            ListaEstudanteNota nota = notaListaService.iniciarNovaTentativa(listaId, estudanteId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("finalizada", false);
            resp.put("config", configParaAluno(listaId, nota));
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
