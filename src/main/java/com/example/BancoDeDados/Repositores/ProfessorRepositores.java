package com.example.BancoDeDados.Repositores;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.example.BancoDeDados.Model.Professor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EnableJpaRepositories
@Repository
public interface ProfessorRepositores extends JpaRepository<Professor, UUID> {
 Optional<Professor> findByEmail(String email);
 List<Professor> findByInstituicaoId(UUID instituicaoId);
}
