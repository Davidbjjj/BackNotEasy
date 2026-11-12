package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** FE-302: convite por e-mail para a instituição trazer professores, coordenadores e alunos. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "convite", indexes = {
        @Index(name = "idx_convite_token", columnList = "token_hash", unique = true),
        @Index(name = "idx_convite_instituicao", columnList = "instituicao_id, criado_em")
})
public class Convite {
    public static final String PENDENTE = "PENDENTE";
    public static final String ACEITO = "ACEITO";
    public static final String CANCELADO = "CANCELADO";

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @Column(nullable = false)
    private String email;

    private String nome;

    /** PROFESSOR | COORDENADOR | ALUNO */
    @Column(nullable = false, length = 15)
    private String papel;

    /** Ids das disciplinas, separados por vírgula. */
    @Column(name = "disciplina_ids", length = 2000)
    private String disciplinaIds = "";

    /** SHA-256 do token; o token em si só existe no e-mail/link. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 10)
    private String status = PENDENTE;

    /** true quando a conta já existe (importação em massa) e o aceite só define a senha. */
    @Column(name = "conta_pre_criada", nullable = false)
    private boolean contaPreCriada = false;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "expira_em", nullable = false)
    private Instant expiraEm;

    @Column(name = "aceito_em")
    private Instant aceitoEm;

    public boolean expirado() {
        return PENDENTE.equals(status) && expiraEm.isBefore(Instant.now());
    }

    /** Status mostrado ao gestor: PENDENTE, ACEITO, EXPIRADO ou CANCELADO. */
    public String statusEfetivo() {
        return expirado() ? "EXPIRADO" : status;
    }
}
