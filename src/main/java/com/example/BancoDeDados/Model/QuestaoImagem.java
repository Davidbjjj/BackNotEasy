package com.example.BancoDeDados.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "questao_imagens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestaoImagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "questao_id", nullable = false)
    @JsonBackReference
    private Questao questao;

    @Column(name = "nome_arquivo", nullable = false)
    private String nomeArquivo;

    @Column(name = "dados_imagem", columnDefinition = "bytea")
    private byte[] dadosImagem;

    @Column(name = "tipo_mime", nullable = false)
    private String tipoMime;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "ordem")
    private Integer ordem;

    @Column(name = "texto_ocr", columnDefinition = "TEXT")
    private String textoOcr;

    // Campos para controlar onde exibir a imagem
    @Builder.Default
    @Column(name = "exibir_no_enunciado")
    private Boolean exibirNoEnunciado = true; // padrão: sempre mostrar no enunciado

    @Builder.Default
    @Column(name = "exibir_nas_alternativas")
    private Boolean exibirNasAlternativas = false; // padrão: não mostrar nas alternativas

    // NOVOS CAMPOS PARA CACHE / CONDICIONAL
    @Column(name = "etag", length = 64)
    private String etag;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
