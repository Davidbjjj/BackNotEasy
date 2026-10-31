package com.example.BancoDeDados.ResponseDTO;

public record ValidateTokenResponseDTO(
        boolean valid,
        String message
) {}