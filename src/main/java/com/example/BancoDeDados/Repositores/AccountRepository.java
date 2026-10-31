package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    Optional<Account> findByProfessorProfile_Id(UUID professorId);

}