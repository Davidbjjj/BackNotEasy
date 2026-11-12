package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class RespostaEstudantes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "questao_id", nullable = false)
    private Questao questao;

    // null enquanto uma resposta aberta não foi corrigida
    @Column
    private Boolean resposta;

    // null em questões abertas
    @Column
    private Integer alternativa;

    // FE-201: texto escrito pelo aluno (questões abertas)
    @Column(name = "texto_resposta", columnDefinition = "TEXT")
    private String textoResposta;

    // FE-201: nota (0 a 10) e feedback do professor; corrigidaEm null = pendente
    @Column(name = "nota_questao", precision = 4, scale = 2)
    private java.math.BigDecimal notaQuestao;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "corrigida_em")
    private Instant corrigidaEm;

    @ManyToOne
    @JoinColumn(name = "estudante_id", nullable = false)
    private Estudante estudante;

    // Instantes absolutos (UTC). O DEFAULT preenche linhas antigas com o momento da migração.
    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false,
            columnDefinition = "timestamp with time zone default current_timestamp")
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false,
            columnDefinition = "timestamp with time zone default current_timestamp")
    private Instant atualizadoEm;

    public RespostaEstudantes(Long id, Questao questao, Boolean resposta, Integer alternativa, Estudante estudante) {
        this.id = id;
        this.questao = questao;
        this.resposta = resposta;
        this.alternativa = alternativa;
        this.estudante = estudante;
    }

    public boolean isCorreta() {
        if (this.questao != null && this.questao.isAberta()) {
            // aberta: "correta" quando corrigida com nota >= 5
            return corrigidaEm != null && notaQuestao != null
                    && notaQuestao.compareTo(new java.math.BigDecimal("5")) >= 0;
        }

        if (this.questao == null || this.alternativa == null || this.questao.getGabarito() == null) {
            return false;
        }
        return this.alternativa.equals(this.questao.getGabarito());
    }
}

