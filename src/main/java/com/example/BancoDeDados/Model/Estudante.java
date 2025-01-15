package com.example.BancoDeDados.Model;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import org.springframework.aot.generate.GeneratedTypeReference;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.BancoDeDados.ResponseDTO.EstudanteLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.EstudanteResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorLoginResponseDTO;

import jakarta.annotation.Generated;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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
    @OneToMany(mappedBy = "estudante", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RespostaEstudantes> respostas;

    public Estudante(EstudanteResponseDTO estudantes) {
        this.nome = estudantes.nome();
        this.email = estudantes.email();
        this.senha = estudantes.senha();
        this.dataNascimento = estudantes.dataNascimento();
        this.instituicao = estudantes.instituicao();
    }

    public Estudante(EstudanteLoginResponseDTO estudanteLoginResponseDTO) {
        this.email = estudanteLoginResponseDTO.email();
        this.senha = estudanteLoginResponseDTO.senha();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Date getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(Date dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getInstituicao() {
        return instituicao;
    }

    public void setInstituicao(String instituicao) {
        this.instituicao = instituicao;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    public List<RespostaEstudantes> getRespostas() {
        return respostas;
    }

    public void setRespostas(List<RespostaEstudantes> respostas) {
        this.respostas = respostas;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_USER"));

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
