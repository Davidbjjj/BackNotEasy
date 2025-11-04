package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisciplinaProfessorResponseDTO {
    private UUID id;
    private String nome;
    private String professorNome;
    private String instituicaoNome;

}