package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Model.Questao;

import java.util.List;
import java.util.UUID;


public record ListaResponseDTO(
        UUID id,
        String titulo,
        String professor
) {
    public ListaResponseDTO(UUID id, String titulo, Professor professor, List<Questao> questoes, List<Estudante> estudantes) {
        this(
                id ,
                titulo,
                professor != null ? professor.getNome() : null
        );
    }
}
