package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Professor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

public class ProfessorDTO {
    private UUID id;
    private String nome;
    private String materia1;
    private String materia2;
    private String instituicao;
    private String email;
    private Date dataNascimento;
    private String escola; // Apenas o nome da escola, não o objeto completo

    // Construtor a partir da entidade Professor
    public ProfessorDTO(Professor professor) {
        this.id = professor.getId();
        this.nome = professor.getNome();
        this.materia1 = professor.getMateria1();
        this.materia2 = professor.getMateria2();
        this.instituicao = professor.getInstituicao();
        this.email = professor.getEmail();
        this.dataNascimento = professor.getDataNascimento();

    }

    // Getters e Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public String getMateria1() { return materia1; }
    public void setMateria1(String materia1) { this.materia1 = materia1; }

    public String getMateria2() { return materia2; }
    public void setMateria2(String materia2) { this.materia2 = materia2; }

    public String getInstituicao() { return instituicao; }
    public void setInstituicao(String instituicao) { this.instituicao = instituicao; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Date getDataNascimento() { return dataNascimento; }
    public void setDataNascimento(Date dataNascimento) { this.dataNascimento = dataNascimento; }


}
