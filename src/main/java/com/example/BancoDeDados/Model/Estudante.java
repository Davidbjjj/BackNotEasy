package com.example.BancoDeDados.Model;

import java.util.*;

import com.example.BancoDeDados.ResponseDTO.EstudanteResponseDTO;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Entity(name = "estudante")
@Table(name = "estudante")

public class Estudante implements UserDetails {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nome;
    @Column(nullable = false)

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private java.util.Date dataNascimento;

    @Column(nullable = false)
    private String instituicao;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String senha;
    @ManyToMany
    @JoinTable(
            name = "estudante_materia",
            joinColumns = @JoinColumn(name = "estudante_id"),
            inverseJoinColumns = @JoinColumn(name = "materia_id")
    )
    private List<Disciplina> disciplina = new ArrayList<>();
    @OneToMany(mappedBy = "estudante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaEstudantes> respostas;

    public Estudante(EstudanteResponseDTO estudantes) {
        this.nome = estudantes.nome();
        this.email = estudantes.email();
        this.senha = estudantes.senha();
        this.dataNascimento = estudantes.dataNascimento();
        this.instituicao = estudantes.instituicao();
    }


    public List<RespostaEstudantes> getRespostas() {
        return respostas;
    }

    public void setRespostas(List<RespostaEstudantes> respostas) {
        this.respostas = respostas;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_ESTUDANTE"));

    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.email;
    }

}
