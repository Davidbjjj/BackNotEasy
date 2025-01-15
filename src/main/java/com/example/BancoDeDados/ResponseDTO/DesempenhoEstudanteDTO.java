package com.example.BancoDeDados.ResponseDTO;

public class DesempenhoEstudanteDTO {
    private Long estudanteId;
    private Long questaoId;
    private boolean respostaCorreta;

    public DesempenhoEstudanteDTO(Integer estudanteId, Integer questaoId, boolean respostaCorreta) {
        this.estudanteId = Long.valueOf(estudanteId);
        this.questaoId = Long.valueOf(questaoId);
        this.respostaCorreta = respostaCorreta;
    }

    public Long getEstudanteId() {
        return estudanteId;
    }

    public Long getQuestaoId() {
        return questaoId;
    }

    public boolean isRespostaCorreta() {
        return respostaCorreta;
    }
}
