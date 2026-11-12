package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** FE-204/207: um acesso do aluno ao app (registrado no máximo a cada 30 minutos). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "acesso_estudante", indexes = {
        @Index(name = "idx_acesso_estudante_em", columnList = "estudante_id, acesso_em")
})
public class AcessoEstudante {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "estudante_id", columnDefinition = "uuid", nullable = false)
    private UUID estudanteId;

    @Column(name = "acesso_em", nullable = false)
    private Instant acessoEm = Instant.now();

    public AcessoEstudante(UUID estudanteId, Instant acessoEm) {
        this.estudanteId = estudanteId;
        this.acessoEm = acessoEm;
    }
}
