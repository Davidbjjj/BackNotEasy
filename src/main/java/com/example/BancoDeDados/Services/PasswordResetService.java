package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.PasswordResetToken;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.PasswordResetTokenRepository;
import com.example.BancoDeDados.ResponseDTO.ValidateTokenResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PasswordResetService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final int TOKEN_EXPIRATION_MINUTES = 15;

    @Transactional
    public boolean requestPasswordReset(String email) {
        Optional<Account> accountOpt = accountRepository.findByEmail(email);

        if (accountOpt.isEmpty()) {
            return true;
        }

        Account account = accountOpt.get();

        tokenRepository.invalidateAllTokensForAccount(account.getId());

        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setAccount(account);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES));
        resetToken.setUsed(false);

        tokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(email, token);

        return true;
    }

    public ValidateTokenResponseDTO validateToken(String token) {
        Optional<PasswordResetToken> resetTokenOpt = tokenRepository.findByToken(token);

        if (resetTokenOpt.isEmpty()) {
            return new ValidateTokenResponseDTO(false, "Token inválido");
        }

        PasswordResetToken resetToken = resetTokenOpt.get();

        if (resetToken.isExpired()) {
            return new ValidateTokenResponseDTO(false, "Token expirado");
        }

        if (resetToken.getUsed()) {
            return new ValidateTokenResponseDTO(false, "Token já utilizado");
        }

        return new ValidateTokenResponseDTO(
                true,
                "Token válido"
        );
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {

        ValidateTokenResponseDTO validation = validateToken(token);
        if (!validation.valid()) {
            return false;
        }

        Optional<PasswordResetToken> resetTokenOpt = tokenRepository.findByToken(token);
        if (resetTokenOpt.isEmpty()) {
            return false;
        }

        PasswordResetToken resetToken = resetTokenOpt.get();
        Account account = resetToken.getAccount();
        account.setSenha(passwordEncoder.encode(newPassword));
        accountRepository.save(account);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        return true;
    }
    private String generateToken() {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = (int) (Math.random() * characters.length());
            token.append(characters.charAt(index));
        }

        return token.toString();
    }
}