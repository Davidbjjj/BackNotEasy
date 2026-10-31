package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlunoMediaDTO {
    private UUID estudanteId;
    private String estudanteNome;
    private Double media; // percentage (0-100)
    private Long respostasCount; // total respostas considered
}

