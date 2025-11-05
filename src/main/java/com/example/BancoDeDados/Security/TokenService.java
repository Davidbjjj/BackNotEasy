package com.example.BancoDeDados.Security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.example.BancoDeDados.Model.Account;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class TokenService {

    private final Set<String> revokedTokens = new HashSet<>();
    private final String secret = "1234";

    public String gerarToken(Account account) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);

            var tokenBuilder = JWT.create()
                    .withIssuer("BancoDeQuestoes")
                    .withSubject(account.getEmail())
                    .withClaim("role", account.getRole().name())
                    .withExpiresAt(generateTokenExpiration());

            if (account.getEstudanteProfile() != null) {
                tokenBuilder
                        .withClaim("userId", account.getEstudanteProfile().getId().toString())
                        .withClaim("nome", account.getEstudanteProfile().getNome());

                if (account.getEstudanteProfile().getInstituicao() != null) {
                    tokenBuilder.withClaim("instituicaoId",
                            account.getEstudanteProfile().getInstituicao().toString());
                }
            }

            if (account.getProfessorProfile() != null) {
                tokenBuilder
                        .withClaim("userId", account.getProfessorProfile().getId().toString())
                        .withClaim("nome", account.getProfessorProfile().getNome());

                if (account.getProfessorProfile().getInstituicao() != null) {
                    tokenBuilder.withClaim("instituicaoId",
                            account.getProfessorProfile().getInstituicao().getId().toString());
                }
            }

            if (account.getInstituicaoProfile() != null) {
                tokenBuilder.withClaim("instituicaoNome", account.getInstituicaoProfile().getId().toString());
            }

            return tokenBuilder.sign(algorithm);
        } catch (JWTCreationException exception) {
            throw new RuntimeException("Erro ao gerar o token", exception);
        }
    }

    public String validarToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("BancoDeQuestoes")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    public UUID getUserIdFromToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decodedJWT = JWT.require(algorithm)
                    .withIssuer("BancoDeQuestoes")
                    .build()
                    .verify(token);

            return UUID.fromString(decodedJWT.getClaim("userId").asString());
        } catch (Exception exception) {
            return null;
        }
    }

    public String getUserTypeFromToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decodedJWT = JWT.require(algorithm)
                    .withIssuer("BancoDeQuestoes")
                    .build()
                    .verify(token);

            return decodedJWT.getClaim("userType").asString();
        } catch (Exception exception) {
            return null;
        }
    }

    public UUID getInstituicaoIdFromToken(String token) {
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Algorithm algorithm = Algorithm.HMAC256(secret);
            var decodedJWT = JWT.require(algorithm)
                    .withIssuer("BancoDeQuestoes")
                    .build()
                    .verify(token);

            var instituicaoClaim = decodedJWT.getClaim("instituicaoId");
            if (instituicaoClaim.isNull()) {
                return null;
            }
            return UUID.fromString(instituicaoClaim.asString());
        } catch (Exception exception) {
            return null;
        }
    }

    public void revokeToken(String token) {
        revokedTokens.add(token);
    }

    public boolean isTokenRevoked(String token) {
        return revokedTokens.contains(token);
    }

    private Instant generateTokenExpiration() {
        return LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.of("-03:00"));
    }
}