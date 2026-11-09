package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

/**
 * Dashboard pessoal do aluno (FE-101).
 * Todos os instantes são strings ISO-8601 com offset (ex.: 2026-11-08T21:30:00-03:00),
 * calculados no fuso do aluno.
 */
public record DashboardAlunoDTO(
        UUID estudanteId,
        String nome,
        String fusoHorario,
        int totalDisciplinas,
        Sequencia sequencia,
        List<ListaEmAndamento> emAndamento,
        List<ProximaEntrega> proximasEntregas,
        List<UltimaNota> ultimasNotas
) {

    /** Dias consecutivos (até hoje ou ontem) com ao menos uma resposta; ultimaAtividadeEm é null sem respostas. */
    public record Sequencia(int diasConsecutivos, String ultimaAtividadeEm) {
    }

    public record ListaEmAndamento(
            UUID listaId,
            String titulo,
            UUID disciplinaId,
            String disciplinaNome,
            int questoesRespondidas,
            int totalQuestoes,
            String ultimaAtividadeEm
    ) {
    }

    /** Lista finalizada com nota (escala 0 a notaMaxima); corrigidaEm = última resposta da lista. */
    public record UltimaNota(
            UUID listaId,
            String titulo,
            UUID disciplinaId,
            String disciplinaNome,
            double nota,
            double notaMaxima,
            String corrigidaEm
    ) {
    }

    public record ProximaEntrega(
            UUID eventoId,
            String titulo,
            UUID disciplinaId,
            String disciplinaNome,
            String prazo
    ) {
    }
}
