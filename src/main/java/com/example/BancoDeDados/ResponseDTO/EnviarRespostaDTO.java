package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public record EnviarRespostaDTO(
        UUID estudanteId,
        Integer questaoId,
        Integer alternativa
) {}