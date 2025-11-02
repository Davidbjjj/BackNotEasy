package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "lista_estudante_nota")
public class ListaEstudanteNota {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "lista_id", nullable = false)
    private Lista lista;

    @ManyToOne
    @JoinColumn(name = "estudante_id", nullable = false)
    private Estudante estudante;

    @Column(name = "nota", precision = 4, scale = 2) // Agora funciona com BigDecimal
    private BigDecimal nota;

    @Column(name = "porcentagem_acertos", precision = 5, scale = 2)
    private BigDecimal porcentagemAcertos;

    @Column(name = "questoes_respondidas")
    private Integer questõesRespondidas;

    @Column(name = "questoes_corretas")
    private Integer questõesCorretas;

    @Column(name = "total_questoes")
    private Integer totalQuestoes;
}