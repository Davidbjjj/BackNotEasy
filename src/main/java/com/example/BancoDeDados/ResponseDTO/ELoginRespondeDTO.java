package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

//cadastro estudante
public record ELoginRespondeDTO(UUID id, String token, String nome) {

}
