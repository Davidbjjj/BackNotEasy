package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

/** {@code alternativa} para questões objetivas; {@code textoResposta} para questões abertas (FE-201). */
public record EnviarRespostaDTO(
        UUID estudanteId,
        Integer questaoId,
        Integer alternativa,
        UUID listaId,
        String textoResposta
) {}
