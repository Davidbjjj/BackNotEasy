package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "questao_alternativas",
       indexes = @Index(name = "idx_questao_alternativa", columnList = "questao_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class QuestaoAlternativa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    @Column(nullable = false)
    private Integer ordem; // 0 = A, 1 = B, 2 = C, 3 = D, 4 = E

    @Column(nullable = false, columnDefinition = "TEXT")
    private String texto;
}

