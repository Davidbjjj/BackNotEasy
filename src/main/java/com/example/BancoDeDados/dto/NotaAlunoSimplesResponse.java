package com.example.BancoDeDados.dto;

import java.util.UUID;

public record NotaAlunoSimplesResponse(UUID estudanteId, String nomeAluno, Double nota) {
}
