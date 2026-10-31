package com.example.BancoDeDados.ResponseDTO;

public record ResetPasswordRequestDTO(
        String token,
        String newPassword
) {}
