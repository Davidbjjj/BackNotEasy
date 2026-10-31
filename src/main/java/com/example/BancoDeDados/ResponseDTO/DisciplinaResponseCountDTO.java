package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record DisciplinaResponseCountDTO(
        UUID id,
        String nome,
        String nomeProfessor,
        String nomeEscola,
        int quantidadeAlunos
) {}

