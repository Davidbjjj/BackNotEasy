package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record DisciplinaUpdateDTO(
        String nome,
        UUID professorId,
        UUID instituicaoId
) {}

