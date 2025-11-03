package com.example.BancoDeDados.Model;

import com.example.BancoDeDados.ResponseDTO.InstituicaoRequest;
import com.example.BancoDeDados.ResponseDTO.IntituicaoResponseDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(of = "id")
@Table(name = "instituicoes")
public class Instituicao implements UserDetails {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String senha;

    @Column(nullable = false)
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date dataDeFundacao;

    @Column(nullable = false)
    private String endereco;

    @Column(nullable = false)
    private String role = "INSTITUICAO";

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> emailsPermitidos = new ArrayList<>();

    public Instituicao(InstituicaoRequest request) {
        this.nome = request.getNome();
        this.email = request.getEmail();
        this.senha = request.getSenha();
        this.endereco = request.getEndereco();
        this.dataDeFundacao = new Date();
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return List.of(new SimpleGrantedAuthority("ROLE_INSTITUICAO"));

    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.email;
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

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
}