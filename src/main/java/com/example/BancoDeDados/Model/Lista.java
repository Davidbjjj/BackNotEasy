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

    // Construtor para inicializar a lista de estudantes
    public Lista(Long id, String titulo, Professor professor, List<Questao> questoes) {
        this.id = id;
        this.titulo = titulo;
        this.professor = professor;
        this.questoes = questoes;
        this.estudantes = new ArrayList<>(); // Inicializando a lista manualmente
    }
}
