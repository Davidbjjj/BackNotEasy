package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Notificação de um usuário. {@code usuarioId} é o userId do JWT (id do perfil: estudante, professor ou instituição). */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notificacao", indexes = {
        @Index(name = "idx_notificacao_usuario_criada", columnList = "usuario_id, created_at")
})
public class Notificacao {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "usuario_id", columnDefinition = "uuid", nullable = false)
    private UUID usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoNotificacao tipo;

    @Column(nullable = false, length = 120)
    private String titulo;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Column(nullable = false)
    private boolean lida = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Rota do front (começa com "/"); null = só informa. */
    @Column(length = 255)
    private String link;
}
