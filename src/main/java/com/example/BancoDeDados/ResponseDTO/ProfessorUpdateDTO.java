package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;
import java.util.UUID;

public record ProfessorUpdateDTO(
        String nome,
        UUID materia1Id,
        UUID materia2Id,
        UUID instituicaoId,
        Date dataNascimento
) {
    // Não inclui email (imutável) nem senha (tem endpoint separado)
}

