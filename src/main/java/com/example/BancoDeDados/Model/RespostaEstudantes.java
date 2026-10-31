package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.*;

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


    public boolean isCorreta() {

        if (this.questao == null || this.alternativa == null || this.questao.getGabarito() == null) {
            return false;
        }
        return this.alternativa.equals(this.questao.getGabarito());
    }
}

