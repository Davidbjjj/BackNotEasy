package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.ListaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListaEventoRepository extends JpaRepository<ListaEvento, UUID> {

    Optional<ListaEvento> findByListaAndEvento(Lista lista, Evento evento);

    boolean existsByListaAndEvento(Lista lista, Evento evento);
    List<ListaEvento> findByListaId(UUID listaId);

    List<ListaEvento> findByEvento(Evento evento);

    void deleteByListaAndEvento(Lista lista, Evento evento);
}