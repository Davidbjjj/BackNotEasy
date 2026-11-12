package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** FE-307: quem alterou o quê, com o valor anterior e o novo. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "evento_auditoria", indexes = @Index(name = "idx_auditoria_inst_data", columnList = "instituicao_id, criado_em"))
public class EventoAuditoria {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instituicao_id", columnDefinition = "uuid", nullable = false)
    private UUID instituicaoId;

    @Column(name = "usuario_id", columnDefinition = "uuid")
    private UUID usuarioId;

    @Column(name = "usuario_nome")
    private String usuarioNome;

    /** Ex.: NOTA_ALTERADA, CONFIG_LISTA, CONVITE_CRIADO, PAPEL_ALTERADO. */
    @Column(nullable = false, length = 40)
    private String acao;

    @Column(length = 40)
    private String entidade;

    @Column(name = "entidade_id", length = 64)
    private String entidadeId;

    @Column(length = 500)
    private String descricao;

    @Column(name = "valor_anterior", length = 1000)
    private String valorAnterior;

    @Column(name = "valor_novo", length = 1000)
    private String valorNovo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();
}
