package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Disciplina;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DisciplinaRepository extends JpaRepository<Disciplina, UUID> {
    List<Disciplina> findByInstituicao_Nome(String nomeEscola);
    Optional<Disciplina> findById(UUID Id);
    Optional<Disciplina> findByNome(String nome);
    List<Disciplina> findByProfessorId(UUID professorId);
    List<Disciplina> findByEstudantesId(UUID estudanteId);
}
