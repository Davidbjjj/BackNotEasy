package com.example.BancoDeDados.ResponseDTO;


import java.util.UUID;


public record PLoginResponseDTO(UUID id, String token, String nome) {
}
