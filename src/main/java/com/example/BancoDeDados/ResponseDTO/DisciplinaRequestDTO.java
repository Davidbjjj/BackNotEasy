package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record DisciplinaRequestDTO(
        String nome,
        UUID professorId,
        UUID instituicaoId
) {}
