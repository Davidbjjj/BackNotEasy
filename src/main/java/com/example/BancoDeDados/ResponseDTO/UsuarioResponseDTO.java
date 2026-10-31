package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;

public record UsuarioResponseDTO(String nome, String email, String senha, Date dataNascimento) {

}
