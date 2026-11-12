package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Aviso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AvisoRepository extends JpaRepository<Aviso, UUID> {
    List<Aviso> findByDisciplinaIdOrderByFixadoDescCriadoEmDesc(UUID disciplinaId);
    long countByDisciplinaIdAndFixadoTrue(UUID disciplinaId);
}
