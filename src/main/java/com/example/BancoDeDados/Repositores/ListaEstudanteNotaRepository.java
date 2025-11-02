package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.ListaEstudanteNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListaEstudanteNotaRepository extends JpaRepository<ListaEstudanteNota, UUID> {

    Optional<ListaEstudanteNota> findByListaIdAndEstudanteId(UUID listaId, UUID estudanteId);

    List<ListaEstudanteNota> findByListaId(UUID listaId);

    List<ListaEstudanteNota> findByEstudanteId(UUID estudanteId);

    boolean existsByListaIdAndEstudanteId(UUID listaId, UUID estudanteId);
}