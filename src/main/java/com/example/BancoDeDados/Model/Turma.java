package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** FE-304: turma de um período, com alunos e disciplinas. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "turma")
public class Turma {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne(optional = false)
    @JoinColumn(name = "periodo_id", nullable = false)
    private Periodo periodo;

    @Column(nullable = false, length = 80)
    private String nome;

    @ManyToMany
    @JoinTable(name = "turma_estudantes", joinColumns = @JoinColumn(name = "turma_id"),
            inverseJoinColumns = @JoinColumn(name = "estudante_id"))
    private List<Estudante> estudantes = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "turma_disciplinas", joinColumns = @JoinColumn(name = "turma_id"),
            inverseJoinColumns = @JoinColumn(name = "disciplina_id"))
    private List<Disciplina> disciplinas = new ArrayList<>();
}
