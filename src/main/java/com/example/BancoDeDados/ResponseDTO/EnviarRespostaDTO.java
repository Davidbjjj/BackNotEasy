package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EnviarRespostaDTO {
    public Integer getQuestaoId() {
        return questaoId;
    }

    public void setQuestaoId(Integer questaoId) {
        this.questaoId = questaoId;
    }

    public Integer getEstudanteId() {
        return estudanteId;
    }

    public void setEstudanteId(Integer estudanteId) {
        this.estudanteId = estudanteId;
    }

    public Boolean getResposta() {
        return resposta;
    }

    public void setResposta(Boolean resposta) {
        this.resposta = resposta;
    }

    private Integer questaoId;
    private Integer estudanteId;
    private Boolean resposta;
}
