package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Questao;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@NoArgsConstructor
@AllArgsConstructor
@Data
public class QuestaoDTO {
    private Integer id;
    private String cabecalho;
    private String enunciado;
    private List<String> alternativas;
    private Integer gabarito;
    private String tituloLista;

    public QuestaoDTO(Questao questao) {
        this.id = questao.getId();
        this.cabecalho = questao.getCabecalho();
        this.enunciado = questao.getEnunciado();
        this.alternativas = questao.getAlternativas();
        this.gabarito = questao.getGabarito();
        this.tituloLista = questao.getLista() != null ?
                questao.getLista().getTitulo() : null;
    }
}