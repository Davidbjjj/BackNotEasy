package com.example.BancoDeDados.ResponseDTO;


import java.util.UUID;

public record EnviarRespostaDTO(
        UUID estudanteId,
        Integer questaoId,
        Integer alternativa,
        UUID listaId
) {}