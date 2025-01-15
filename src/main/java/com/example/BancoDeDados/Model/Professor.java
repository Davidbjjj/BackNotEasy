package com.example.BancoDeDados.Model;

import com.example.BancoDeDados.ResponseDTO.ProfessorLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorResponseDTO;
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

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(of = "id")
@Entity(name = "professor")
@Table(name = "professor")
public class Professor implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

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
    @Enumerated(EnumType.STRING)
    private ProfessorRole role;

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
        this.role = professorDTO.role();
    }

    public Professor(Integer id ,String email, String encriptarSenha, ProfessorRole role) {
        this.id=id;
        this.email = email;
        this.senha = encriptarSenha;
        this.role = role;
    }

    public Professor(ProfessorLoginResponseDTO professorLoginResponseDTO) {
        this.id=professorLoginResponseDTO.id();
        this.email = professorLoginResponseDTO.email();
        this.senha = professorLoginResponseDTO.senha();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == ProfessorRole.PROFESSOR)
            return List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR"), new SimpleGrantedAuthority("ROLE_USER"));
        else
            return List.of(new SimpleGrantedAuthority("ROLE_USER"));

    }

    @Override
    public String getPassword() {
        return this.senha;
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

    public ProfessorRole getRole() {
        return role;
    }

    public void setRole(ProfessorRole role) {
        this.role = role;
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

}
