package com.example.BancoDeDados.ResponseDTO;// LoginResponseDTO.java

import java.util.UUID;

public record LoginResponseDTO(
        UUID id,
        String email,
        String token
) {}
