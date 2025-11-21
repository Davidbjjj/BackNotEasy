package com.example.BancoDeDados.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "caminho_arquivo", nullable = false)
    private String caminhoArquivo;

    @Column(name = "url_publica")
    private String urlPublica;

    @Column(name = "tipo_mime")
    private String tipoMime;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @Column(name = "ordem")
    private Integer ordem;

    @Column(name = "texto_ocr", columnDefinition = "TEXT")
    private String textoOcr;

    // Campos para controlar onde exibir a imagem
    @Column(name = "exibir_no_enunciado")
    private Boolean exibirNoEnunciado = true; // padrão: sempre mostrar no enunciado

    @Column(name = "exibir_nas_alternativas")
    private Boolean exibirNasAlternativas = false; // padrão: não mostrar nas alternativas
}

