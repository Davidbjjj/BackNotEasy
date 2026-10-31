package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);

    List<Account> findByRole(Role role);

    Optional<Account> findByProfessorProfile_Id(UUID professorId);

    Optional<Account> findByEstudanteProfile_Id(UUID estudanteId);

    Optional<Account> findByPaisProfile_Id(UUID paisId);

    Optional<Account> findByInstituicaoProfile_Id(UUID instituicaoId);
}