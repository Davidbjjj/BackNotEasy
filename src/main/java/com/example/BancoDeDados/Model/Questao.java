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


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column
    private String cabecalho;

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
