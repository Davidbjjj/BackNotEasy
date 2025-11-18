package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtividadeConcluidaDTO {
    private UUID estudanteId;
    private String estudanteNome;
    private UUID disciplinaId;
    private String disciplinaNome;
    private Long atividadesConcluidas;
}

