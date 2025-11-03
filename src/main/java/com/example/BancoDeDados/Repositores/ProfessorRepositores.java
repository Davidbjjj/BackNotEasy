package com.example.BancoDeDados.Repositores;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Component;

import com.example.BancoDeDados.Model.Professor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@EnableJpaRepositories
@Repository
public interface ProfessorRepositores extends JpaRepository<Professor, UUID> {
 Optional<Professor> findByEmail(String email);
 @Query("SELECT p FROM professor p WHERE p.email = :email AND p.instituicao.id = :instituicaoId")
 Optional<Professor> findByEmailAndInstituicaoId(String email, UUID instituicaoId);
}

