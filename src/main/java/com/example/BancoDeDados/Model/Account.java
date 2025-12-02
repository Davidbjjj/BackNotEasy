package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Data
@Table(name = "account")
public class Account implements UserDetails {

    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(unique=true, nullable=false)
    private String email;

    @Column(nullable=false)
    private String senha;

    @Enumerated(EnumType.STRING)
    private Role role;

    @OneToOne
    @JoinColumn(name = "professor_id")
    private Professor professorProfile;

    @OneToOne
    @JoinColumn(name = "estudante_id")
    private Estudante estudanteProfile;

    @OneToOne
    @JoinColumn(name = "instituicao_id")
    private Instituicao instituicaoProfile;

    @OneToOne
    @JoinColumn(name = "pais_id")
    private Pais paisProfile;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return senha; }
    @Override public String getUsername() { return email; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
