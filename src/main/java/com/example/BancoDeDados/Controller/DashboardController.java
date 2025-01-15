package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.DesempenhoEstudanteDTO;
import com.example.BancoDeDados.Services.DashboardService;
import com.example.BancoDeDados.Services.RespostaEstudantesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    RespostaEstudantesService respostaEstudantesService;

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

}
