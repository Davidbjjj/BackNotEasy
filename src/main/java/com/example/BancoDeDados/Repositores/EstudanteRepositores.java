package com.example.BancoDeDados.Repositores;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.BancoDeDados.Model.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Component;

import com.example.BancoDeDados.Model.Estudante;

@Component
public interface EstudanteRepositores extends JpaRepository<Estudante, UUID> {
    Optional<Estudante> findByNome(String nome);

    Optional<Estudante> findByEmail(String email);

    Optional<Estudante> findByDisciplinaContaining(Disciplina disciplina);

    List<Estudante> findByInstituicao(String instituicao);

    @Query(value = "SELECT e.* FROM estudante e INNER JOIN instituicao i ON e.instituicao = i.nome WHERE i.id = :instituicaoId", nativeQuery = true)
    List<Estudante> findByInstituicaoId(@Param("instituicaoId") UUID instituicaoId);
}

