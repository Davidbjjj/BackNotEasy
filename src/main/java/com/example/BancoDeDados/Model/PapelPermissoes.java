package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** FE-306: permissões de um papel numa instituição (linha ausente = padrão do sistema). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "papel_permissoes", uniqueConstraints = @UniqueConstraint(columnNames = {"instituicao_id", "papel"}))
public class PapelPermissoes {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instituicao_id", columnDefinition = "uuid", nullable = false)
    private UUID instituicaoId;

    @Column(nullable = false, length = 15)
    private String papel;

    /** Chaves separadas por vírgula. */
    @Column(nullable = false, length = 1000)
    private String permissoes = "";
}
