package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class EventoResumoResponse {
    private UUID id;
    private String titulo;
    private LocalDateTime data;
    private int totalAlunos;
    private int notasAtribuidas;
}
