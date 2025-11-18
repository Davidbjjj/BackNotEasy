package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EstudanteSimplesDTO {
    private UUID id;
    private String nome;
    private String email;
    private Date dataNascimento;
    private String instituicao;
}

