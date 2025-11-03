package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Professor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfessorDTO {
    private UUID id;
    private String nome;
    private String materia1;
    private String materia2;
    private UUID instituicaoId; // Mude o nome para refletir que é o ID
    private String instituicaoNome; // Adicione este campo para o nome
    private String email;
    private Date dataNascimento;

    // Construtor a partir da entidade Professor
    public ProfessorDTO(Professor professor) {
        this.id = professor.getId();
        this.nome = professor.getNome();
        this.materia1 = professor.getMateria1();
        this.materia2 = professor.getMateria2();
        this.instituicaoId = professor.getInstituicao() != null ? professor.getInstituicao().getId() : null;
        this.instituicaoNome = professor.getInstituicao() != null ? professor.getInstituicao().getNome() : null;
        this.email = professor.getEmail();
        this.dataNascimento = professor.getDataNascimento();
    }
}
