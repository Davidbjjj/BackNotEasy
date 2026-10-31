package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record RespostaRequest(
        UUID estudanteId,
        Integer questaoId,
        Integer alternativaEscolhida
) {}