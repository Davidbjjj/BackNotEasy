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
    private Integer id;
    private String titulo;
    private String professorNome;
    private List<QuestaoResponseDTO> questões;
    private Integer totalQuestoes;

    public ListaCompletaResponseDTO(UUID id, String titulo, String nome, List<QuestaoResponseDTO> questaoDTOs) {

    }
}