package com.example.BancoDeDados.Model;

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

    @ManyToOne
    @JoinColumn(name = "instituicao_id", nullable = false)
    private Instituicao instituicao; // Deve ser do tipo Instituicao, não UUID

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dataNascimento;

    // Construtor que recebe DTO e Instituicao
    public Professor(ProfessorResponseDTO professorDTO, Instituicao instituicao) {
        this.nome = professorDTO.nome();
        this.materia1 = professorDTO.materia1();
        this.materia2 = professorDTO.materia2();
        this.instituicao = instituicao; // Agora é o objeto Instituicao
        this.email = professorDTO.email();
        this.senha = professorDTO.senha();
        this.dataNascimento = professorDTO.dataNascimento();
    }

    // Construtor simplificado (mantenha se estiver usando)
    public Professor(UUID id, String email, String encriptarSenha) {
        this.id = id;
        this.email = email;
        this.senha = encriptarSenha;
    }

    // Remove o construtor antigo que causava conflito
    // public Professor(ProfessorResponseDTO professorDTO) { ... }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_PROFESSOR"));
    }

    @Override
    public String getPassword() {
        return this.senha; // Retorna a senha real
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
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
        return this.instituicao != null ? this.instituicao.getNome() : null;
    }
}