package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MetricasAlunoDTO {
    private UUID alunoId;
    private String nomeAluno;
    private UUID disciplinaId;
    private String nomeDisciplina;

    // Métricas de Eventos
    private Integer totalEventos;
    private Integer eventosEntregues;
    private Integer eventosPendentes;
    private Double mediaEventos;
    private Double mediaPercentualEventos;

    // Métricas de Listas
    private Integer totalListas;
    private Integer listasRespondidas;
    private Double mediaListas;
    private Double mediaPercentualListas;

    // Métricas Gerais
    private Double mediaGeral;
    private Double taxaEntrega; // percentual de entregas realizadas
    private String status; // "Excelente", "Bom", "Regular", "Precisa Melhorar"

    // Detalhes
    private List<EventoDetalhe> eventos;
    private List<ListaDetalhe> listas;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EventoDetalhe {
        private UUID eventoId;
        private String titulo;
        private Double nota;
        private Double notaMaxima;
        private Double percentual;
        private String status;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ListaDetalhe {
        private UUID listaId;
        private String titulo;
        private Double nota;
        private Integer totalQuestoes;
        private Integer questoesRespondidas;
        private Double percentual;
    }
}

