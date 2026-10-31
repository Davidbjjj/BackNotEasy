package com.example.BancoDeDados.dto;

import java.time.LocalDate;
import java.util.UUID;

public record EventoListaDTO(
        UUID idEvento,
        String nomeEvento,
        LocalDate prazo,
        String nomeDisciplina,
        Double notaMaxima
) {
}
