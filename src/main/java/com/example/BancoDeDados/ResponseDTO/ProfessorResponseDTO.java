package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;
import java.util.UUID;


public record ProfessorResponseDTO(
        String nome,
        UUID materia1Id,
        UUID materia2Id,
        UUID instituicaoId,
        String email,
        String senha,
        Date dataNascimento
) {}
