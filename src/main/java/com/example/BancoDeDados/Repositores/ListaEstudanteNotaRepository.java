package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.ListaEstudanteNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Dashboard pessoal: listas em andamento (respondeu ao menos uma questão e ainda não finalizou;
    // inclui a que já tem todas respondidas mas falta finalizar)
    @Query("SELECT n FROM ListaEstudanteNota n JOIN FETCH n.lista l LEFT JOIN FETCH l.disciplina " +
            "WHERE n.estudante.id = :estudanteId AND n.finalizada = false " +
            "AND n.questoesRespondidas > 0")
    List<ListaEstudanteNota> findEmAndamentoByEstudanteId(@Param("estudanteId") UUID estudanteId);

    // Dashboard pessoal: listas finalizadas com nota
    @Query("SELECT n FROM ListaEstudanteNota n JOIN FETCH n.lista l LEFT JOIN FETCH l.disciplina " +
            "WHERE n.estudante.id = :estudanteId AND n.finalizada = true AND n.nota IS NOT NULL")
    List<ListaEstudanteNota> findFinalizadasComNotaByEstudanteId(@Param("estudanteId") UUID estudanteId);

    // Buscar notas de um estudante em várias listas
    List<ListaEstudanteNota> findByEstudanteIdAndListaIdIn(UUID estudanteId, List<UUID> listaIds);
}