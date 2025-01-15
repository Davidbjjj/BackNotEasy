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

    @ManyToOne
    @JoinColumn(name = "estudante_id", nullable = false)
    private Estudante estudante;

    public boolean isCorreta() {
        return resposta.equals(questao.getGabarito());
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Questao getQuestao() {
        return questao;
    }

    public void setQuestao(Questao questao) {
        this.questao = questao;
    }

    public Boolean getResposta() {
        return resposta;
    }

    public void setResposta(Boolean resposta) {
        this.resposta = resposta;
    }

    public Estudante getEstudante() {
        return estudante;
    }

    public void setEstudante(Estudante estudante) {
        this.estudante = estudante;
    }
}
