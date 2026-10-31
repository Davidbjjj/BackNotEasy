package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.PasswordResetToken;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByToken(String token);

    Optional<PasswordResetToken> findByAccountIdAndUsedFalse(UUID accountId);

    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetToken prt SET prt.used = true WHERE prt.account.id = :accountId AND prt.used = false")
    void invalidateAllTokensForAccount(@Param("accountId") UUID accountId);

    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken prt WHERE prt.expiryDate < CURRENT_TIMESTAMP")
    void deleteExpiredTokens();
}