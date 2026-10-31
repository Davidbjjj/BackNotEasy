package com.example.BancoDeDados.Repositores;


import com.example.BancoDeDados.Model.Pais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaisRepositores extends JpaRepository<Pais, Integer> {
    Optional<Pais> findByNome(String nome);

    Optional<Pais> findByEmail(String email);
}
