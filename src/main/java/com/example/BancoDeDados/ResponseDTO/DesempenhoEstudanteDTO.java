package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DesempenhoEstudanteDTO {
    private UUID estudanteId;
    private Integer questaoId;
    private boolean respostaCorreta;

}
