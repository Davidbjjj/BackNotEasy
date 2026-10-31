package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;

public record PaisResponseDTO(String nome, String email, String senha, String telefone, String cpf, Date dataNascimento) {
}
