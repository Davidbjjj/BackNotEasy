package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** FE-208: exportação de notas gerada em segundo plano. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "exportacao", indexes = @Index(name = "idx_exportacao_professor", columnList = "professor_id, criada_em"))
public class Exportacao {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "professor_id", columnDefinition = "uuid", nullable = false)
    private UUID professorId;

    /** "LISTA" ou "DISCIPLINA". */
    @Column(nullable = false, length = 12)
    private String escopo;

    @Column(name = "alvo_id", columnDefinition = "uuid", nullable = false)
    private UUID alvoId;

    @Column(name = "alvo_nome")
    private String alvoNome;

    /** "CSV" ou "PDF". */
    @Column(nullable = false, length = 5)
    private String formato;

    @Column(length = 500)
    private String colunas;

    /** PROCESSANDO | PRONTA | ERRO */
    @Column(nullable = false, length = 12)
    private String status = "PROCESSANDO";

    @Column(name = "nome_arquivo")
    private String nomeArquivo;

    @Column(length = 300)
    private String erro;

    @Column(name = "dados", columnDefinition = "bytea")
    
    private byte[] dados;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm = Instant.now();

    @Column(name = "concluida_em")
    private Instant concluidaEm;
}
