package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** FE-209: aviso do professor no mural da disciplina. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "aviso", indexes = {
        @Index(name = "idx_aviso_disciplina", columnList = "disciplina_id, fixado, criado_em")
})
public class Aviso {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "disciplina_id", nullable = false)
    private Disciplina disciplina;

    @ManyToOne(optional = false)
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @Column(nullable = false, length = 150)
    private String titulo;

    /** Markdown simples (negrito, itálico, listas, links); o front renderiza sem HTML. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(nullable = false)
    private boolean fixado = false;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "anexo_nome")
    private String anexoNome;

    @Column(name = "anexo_tipo", length = 100)
    private String anexoTipo;

    @Column(name = "anexo_dados", columnDefinition = "bytea")
    
    private byte[] anexoDados;
}
