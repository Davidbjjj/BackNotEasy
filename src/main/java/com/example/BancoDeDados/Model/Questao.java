package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity
@Table(name = "questao")
public class Questao {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Lob
    @Column(columnDefinition = "text")
    private String cabecalho;

    @Lob
    @Column(columnDefinition = "text")
    private String enunciado;

    // Mudar o tipo da coluna da coleção para TEXT para suportar alternativas longas
    @ElementCollection
    @CollectionTable(name = "questao_alternativas", joinColumns = @JoinColumn(name = "questao_id"))
    @Column(name = "alternativas", columnDefinition = "text")
    @BatchSize(size = 50)
    private List<String> alternativas;

    @Column
    private Integer gabarito;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_id")
    private Lista lista;


    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaEstudantes> respostasEstudantes;


}
