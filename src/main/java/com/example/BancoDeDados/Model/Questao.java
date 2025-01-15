package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity(name = "questoes")
@Table(name = "questoes")
public class Questao {

    @Column
    public void setCabecalho(String cabecalho) {
        this.cabecalho = cabecalho;
    }

    @Column
    public void setEnunciado(String enunciado) {
        this.enunciado = enunciado;
    }

    @Column
    public void setAlternativas(List<String> alternativas) {
        this.alternativas = alternativas;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column
    private String cabecalho;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCabecalho() {
        return cabecalho;
    }

    public String getEnunciado() {
        return enunciado;
    }

    public List<String> getAlternativas() {
        return alternativas;
    }

    public Integer getGabarito() {
        return gabarito;
    }

    public void setGabarito(Integer gabarito) {
        this.gabarito = gabarito;
    }

    public List<RespostaEstudantes> getRespostasEstudantes() {
        return respostasEstudantes;
    }

    public void setRespostasEstudantes(List<RespostaEstudantes> respostasEstudantes) {
        this.respostasEstudantes = respostasEstudantes;
    }

    @Column
    @Lob
    private String enunciado;
    @ElementCollection
    private List<String> alternativas;
    @Column
    private Integer gabarito;
    @ManyToOne
    @JoinColumn(name = "lista_id")
    private Lista lista;


    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaEstudantes> respostasEstudantes;


}
