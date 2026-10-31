package com.example.BancoDeDados.ResponseDTO;

import java.util.Date;

public record EstudanteResponseDTO(String nome, String email, String senha, Date dataNascimento, String instituicao) {

}
