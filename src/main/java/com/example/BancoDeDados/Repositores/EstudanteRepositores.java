package com.example.BancoDeDados.Repositores;

import java.util.Optional;
import java.util.UUID;

import com.example.BancoDeDados.Model.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;

import com.example.BancoDeDados.Model.Estudante;

@Component
public interface EstudanteRepositores extends JpaRepository<Estudante, UUID> {
    Optional<Estudante> findByNome(String nome);

    Optional<Estudante> findByEmail(String email);

    Optional<Estudante> findByDisciplinaContaining(Disciplina disciplina);
}
