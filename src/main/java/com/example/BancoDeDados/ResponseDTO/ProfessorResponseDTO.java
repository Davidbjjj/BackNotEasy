package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;
import java.util.UUID;


public record ProfessorResponseDTO(
        String nome,
        String materia1,
        String materia2,
        UUID instituicaoId,  // Mantém como UUID para a requisição
        String email,
        String senha,
        Date dataNascimento
) {}
