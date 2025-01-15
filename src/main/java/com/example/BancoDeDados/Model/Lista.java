package com.example.BancoDeDados.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "listas")
public class Lista {
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTitulo() {
        return titulo;
    }

    public Professor getProfessor() {
        return professor;
    }

    public void setProfessor(Professor professor) {
        this.professor = professor;
    }

    public List<Questao> getQuestoes() {
        return questoes;
    }

    public void setQuestoes(List<Questao> questoes) {
        this.questoes = questoes;
    }

    public List<Estudante> getEstudantes() {
        return estudantes;
    }

    public void setEstudantes(List<Estudante> estudantes) {
        this.estudantes = estudantes;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    @JsonBackReference
    private Professor professor;

    @ManyToMany
    @JoinTable(
            name = "lista_questoes",
            joinColumns = @JoinColumn(name = "lista_id"),
            inverseJoinColumns = @JoinColumn(name = "questao_id")
    )
    private List<Questao> questoes;

    @ManyToMany
    @JoinTable(
            name = "lista_estudantes",
            joinColumns = @JoinColumn(name = "lista_id"),
            inverseJoinColumns = @JoinColumn(name = "estudante_id")
    )
    private List<Estudante> estudantes;

    public Lista(Long id, String titulo, Professor professor, List<Questao> questoes) {
        this.id = id;
        this.titulo = titulo;
        this.professor = professor;
        this.questoes = questoes;
        this.estudantes = new ArrayList<>();
    }
}
