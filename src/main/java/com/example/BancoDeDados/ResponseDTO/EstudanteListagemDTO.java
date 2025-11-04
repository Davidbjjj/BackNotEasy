package com.example.BancoDeDados.ResponseDTO;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public record EstudanteListagemDTO(
        UUID id,
        String nome,
        Date dataNascimento,
        String instituicao,
        String email,
        List<DisciplinaRequestDTO> disciplinas
) {
    public EstudanteListagemDTO {
        // Validação opcional no construtor compacto do record
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome não pode ser nulo ou vazio");
        }
    }
}