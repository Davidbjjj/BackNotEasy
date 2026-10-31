package com.example.BancoDeDados.Model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;


@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@Entity
public class NotaEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Min(0)
    private Double nota;
    private String observacao;

    @Enumerated(EnumType.STRING)
    private StatusEntrega statusEntrega = StatusEntrega.PENDENTE;

    private String comentarioEntrega;

    @ElementCollection
    @CollectionTable(name = "nota_evento_arquivos", joinColumns = @JoinColumn(name = "nota_evento_id"))
    @Column(name = "arquivo")
    private List<String> arquivosEntrega = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "estudante_id")
    @JsonBackReference
    private Estudante estudante;

    @ManyToOne
    @JoinColumn(name = "evento_id")
    @JsonBackReference
    private Evento evento;

    @ManyToOne
    @JoinColumn(name = "professor_id", referencedColumnName = "id")
    @JsonBackReference
    private Professor professor;

    public enum StatusEntrega {
        PENDENTE(0, "Pendente de entrega"),
        ENTREGUE(1, "Entregue");

        private final int codigo;
        private final String descricao;

        StatusEntrega(int codigo, String descricao) {
            this.codigo = codigo;
            this.descricao = descricao;
        }

        public int getCodigo() { return codigo; }
        public String getDescricao() { return descricao; }

        public static StatusEntrega fromCodigo(int codigo) {
            for (StatusEntrega status : values()) {
                if (status.getCodigo() == codigo) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Código inválido: " + codigo);
        }
    }


}