package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Exportacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExportacaoRepository extends JpaRepository<Exportacao, UUID> {
    List<Exportacao> findTop20ByProfessorIdOrderByCriadaEmDesc(UUID professorId);
}
