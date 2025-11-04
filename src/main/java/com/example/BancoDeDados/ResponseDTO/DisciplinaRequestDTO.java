package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record DisciplinaRequestDTO(
        String nome,
        String emailProfessor,
        UUID instituicaoId
) {}
