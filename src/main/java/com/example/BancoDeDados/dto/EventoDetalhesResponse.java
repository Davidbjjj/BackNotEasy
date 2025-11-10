package com.example.BancoDeDados.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record EventoDetalhesResponse(
        UUID idEvento,
        String nomeEvento,
        String descricao,
        LocalDate prazo,
        DisciplinaSimplesDTO disciplina,
        ProfessorSimplesDTO professor,
        List<NotaAlunoSimplesResponse> notas,
        List<ListaSimplesResponse> listas
) {
}
