package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.ResponseDTO.DesempenhoEstudanteDTO;
import com.example.BancoDeDados.Services.DashboardService;
import com.example.BancoDeDados.Services.NotaService;
import com.example.BancoDeDados.Services.RespostaEstudantesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    RespostaEstudantesService respostaEstudantesService;

    private NotaService notaService;
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/lista/{listaId}")
    public ResponseEntity<?> getDashboardByLista(@PathVariable Long listaId) {
        try {
            var dashboard = dashboardService.getDashboardByListaId(listaId);
            return ResponseEntity.ok(dashboard);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro: " + e.getMessage());
        }
    }
    @GetMapping("/desempenho/lista/{listaId}")
    public ResponseEntity<List<DesempenhoEstudanteDTO>> getDesempenhoPorLista(@PathVariable Long listaId) {
        List<DesempenhoEstudanteDTO> desempenho = respostaEstudantesService.buscarDesempenhoPorLista(listaId);
        return ResponseEntity.ok(desempenho);
    }

    @GetMapping("/media-geral")
    public Double mediaGeral() {
        return notaService.calcularMediaGeral();
    }

    @GetMapping("/ranking-disciplinas")
    public List<Map<String, Object>> ranking() {
        return notaService.getRankingDisciplinas();
    }


    @GetMapping("/filtro-disciplina/{id}")
    public List<Map<String, Object>> filtro(@PathVariable Long id) {
        return notaService.getPorDisciplina(id);
    }



}
