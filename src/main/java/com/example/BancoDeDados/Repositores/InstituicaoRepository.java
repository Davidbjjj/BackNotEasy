package com.example.BancoDeDados.Repositores;


import com.example.BancoDeDados.Model.Instituicao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface InstituicaoRepository extends JpaRepository<Instituicao, UUID> {
    Optional<Instituicao> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Instituicao> findByNome(String nome);
}
