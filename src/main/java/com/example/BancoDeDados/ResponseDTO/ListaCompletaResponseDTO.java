package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListaCompletaResponseDTO {
    private UUID id;
    private String titulo;
    private String professorNome;
    private List<QuestaoResponseDTO> questoes;
    private Integer totalQuestoes;

    public ListaCompletaResponseDTO(UUID id, String titulo, String nome, List<QuestaoResponseDTO> questaoDTOs) {
        this.id = id;
        this.titulo = titulo;
        this.professorNome = nome;
        this.questoes = questaoDTOs;
        this.totalQuestoes = questaoDTOs != null ? questaoDTOs.size() : 0;
    }

    // Explicit getters (help static analysis that may not process Lombok)
    public UUID getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getProfessorNome() {
        return professorNome;
    }

    public List<QuestaoResponseDTO> getQuestoes() {
        return questoes;
    }

    public Integer getTotalQuestoes() {
        return totalQuestoes;
    }
}