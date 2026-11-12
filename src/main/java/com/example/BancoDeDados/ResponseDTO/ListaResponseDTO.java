package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Model.Questao;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


/**
 * Lista com as regras de aplicação (FE-205) e o status de publicação (FE-206).
 * Campos de regra nulos = sem restrição; {@code status} nulo em listas antigas equivale a PUBLICADA.
 */
public record ListaResponseDTO(
        UUID id,
        String titulo,
        String professor,
        UUID disciplinaId,
        String disciplinaNome,
        String status,
        Instant abreEm,
        Instant fechaEm,
        Integer maxTentativas,
        String regraNota,
        Integer tempoLimiteMinutos,
        boolean embaralharQuestoes,
        boolean embaralharAlternativas
) {
    public ListaResponseDTO(UUID id, String titulo, String professor) {
        this(id, titulo, professor, null, null, "PUBLICADA", null, null, null, null, null, false, false);
    }

    public ListaResponseDTO(UUID id, String titulo, Professor professor, List<Questao> questoes, List<Estudante> estudantes) {
        this(
                id ,
                titulo,
                professor != null ? professor.getNome() : null
        );
    }

    public static ListaResponseDTO de(Lista l) {
        return new ListaResponseDTO(
                l.getId(),
                l.getTitulo(),
                l.getProfessor() != null ? l.getProfessor().getNome() : null,
                l.getDisciplina() != null ? l.getDisciplina().getId() : null,
                l.getDisciplina() != null ? l.getDisciplina().getNome() : null,
                l.isRascunho() ? "RASCUNHO" : "PUBLICADA",
                l.getAbreEm(),
                l.getFechaEm(),
                l.getMaxTentativas(),
                l.getRegraNota() == null ? "MAIOR" : l.getRegraNota(),
                l.getTempoLimiteMinutos(),
                l.isEmbaralharQuestoes(),
                l.isEmbaralharAlternativas()
        );
    }
}
