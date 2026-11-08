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

    @Column(nullable = false)
    private Boolean resposta;

    @Column(nullable = false)
    private Integer alternativa;

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

        if (this.questao == null || this.alternativa == null || this.questao.getGabarito() == null) {
            return false;
        }
        return this.alternativa.equals(this.questao.getGabarito());
    }
}

