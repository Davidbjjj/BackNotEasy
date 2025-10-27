package com.example.BancoDeDados.Model;

import com.example.BancoDeDados.ResponseDTO.ProfessorLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorResponseDTO;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(of = "id")
@Entity(name = "professor")
@Table(name = "professor")
public class Professor implements UserDetails {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column
    private String nome;

    @Column(nullable = false)
    private String materia1;

    @Column(nullable = false)
    private String materia2;

    @Column(nullable = false)
    private String instituicao;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;


    @Column
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dataNascimento;

    public Professor(ProfessorResponseDTO professorDTO) {
        this.nome = professorDTO.nome();
        this.materia1 = professorDTO.materia1();
        this.materia2 = professorDTO.materia2();
        this.instituicao = professorDTO.instituicao();
        this.email = professorDTO.email();
        this.senha = professorDTO.senha();
        this.dataNascimento = professorDTO.dataNascimento();
    }

    public Professor(UUID id ,String email, String encriptarSenha) {
        this.id=id;
        this.email = email;
        this.senha = encriptarSenha;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR"));

    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getMateria1() {
        return materia1;
    }

    public void setMateria1(String materia1) {
        this.materia1 = materia1;
    }

    public String getMateria2() {
        return materia2;
    }

    public void setMateria2(String materia2) {
        this.materia2 = materia2;
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


    public Date getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(Date dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @JsonIgnore
    public Object getEscola() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEscola'");
    }

}
