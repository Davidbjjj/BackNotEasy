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

    // Regra de liberação do gabarito: o professor libera; alunos só veem a revisão
    // (gabarito/explicação) se a lista estiver liberada E eles tiverem finalizado.
    @Column(name = "gabarito_liberado", nullable = false, columnDefinition = "boolean default false")
    private boolean gabaritoLiberado = false;

    // FE-205: janela de resposta, tentativas e regras. Null = sem restrição.
    @Column(name = "abre_em")
    private java.time.Instant abreEm;

    @Column(name = "fecha_em")
    private java.time.Instant fechaEm;

    @Column(name = "max_tentativas")
    private Integer maxTentativas;

    // "MAIOR" (padrão) ou "ULTIMA"
    @Column(name = "regra_nota", length = 10)
    private String regraNota;

    @Column(name = "tempo_limite_minutos")
    private Integer tempoLimiteMinutos;

    @Column(name = "embaralhar_questoes", nullable = false, columnDefinition = "boolean default false")
    private boolean embaralharQuestoes = false;

    @Column(name = "embaralhar_alternativas", nullable = false, columnDefinition = "boolean default false")
    private boolean embaralharAlternativas = false;

    // FE-206: "RASCUNHO" (invisível ao aluno) ou "PUBLICADA". Null (listas antigas) = publicada.
    @Column(length = 12)
    private String status;

    public boolean isRascunho() {
        return "RASCUNHO".equals(status);
    }

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

    @OneToMany(mappedBy = "lista", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<ListaEstudanteNota> notas = new ArrayList<>();

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