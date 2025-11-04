package com.example.BancoDeDados.Model;

import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "listas")
public class Lista {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(nullable = false)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "professor_id", nullable = false)
    @JsonBackReference
    private Professor professor;

    // CORREÇÃO: Remover @JsonManagedReference ou usar @JsonIgnore
    @OneToMany(mappedBy = "lista", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // @JsonManagedReference // REMOVA ESTA ANOTAÇÃO
    @JsonIgnore // OU use @JsonIgnore para evitar loop na serialização
    private List<Questao> questoes;

    @ManyToOne
    @JoinColumn(name = "disciplina_id")
    private Disciplina disciplina;

    @ManyToMany
    @JoinTable(
            name = "lista_estudantes",
            joinColumns = @JoinColumn(name = "lista_id"),
            inverseJoinColumns = @JoinColumn(name = "estudante_id")
    )
    private List<Estudante> estudantes;

    @OneToMany(mappedBy = "lista", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("lista-eventos")
    private List<ListaEvento> eventos = new ArrayList<>();

    // Métodos auxiliares para gerenciar a relação bidirecional
    public void adicionarQuestao(Questao questao) {
        if (questoes == null) {
            questoes = new ArrayList<>();
        }
        if (questao != null && !questoes.contains(questao)) {
            questoes.add(questao);
            questao.setLista(this);
        }
    }

    public void removerQuestao(Questao questao) {
        if (questoes != null && questao != null) {
            questoes.remove(questao);
            questao.setLista(null);
        }
    }

    public void adicionarEstudante(Estudante estudante) {
        if (estudantes == null) {
            estudantes = new ArrayList<>();
        }
        if (estudante != null && !estudantes.contains(estudante)) {
            estudantes.add(estudante);
        }
    }

    public void removerEstudante(Estudante estudante) {
        if (estudantes != null && estudante != null) {
            estudantes.remove(estudante);
        }
    }
}