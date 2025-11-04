package com.example.BancoDeDados.Model;

import com.example.BancoDeDados.ResponseDTO.MateriaResponseDTO;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "disciplina")
public class Disciplina {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @ManyToOne
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao;

    @ManyToOne
    @JoinColumn(name = "professor_id", nullable = false)
    private Professor professor;

    @ManyToMany
    @JoinTable(
        name = "disciplina_estudantes",
        joinColumns = @JoinColumn(name = "disciplina_id"),
        inverseJoinColumns = @JoinColumn(name = "estudante_id")
    )
    private List<Estudante> estudantes = new ArrayList<>();

    @OneToMany(mappedBy = "disciplina", cascade = CascadeType.ALL)
    private List<Evento> eventos = new ArrayList<>();


    public Disciplina(MateriaResponseDTO dto, Professor professor, Instituicao instituicao) {
        this.nome = dto.getNome();
        this.professor = professor;
        this.instituicao = instituicao;
    }
}
