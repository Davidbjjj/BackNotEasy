package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InstituicaoResponse {

    private UUID id;
    private String nome;
    private String email;
    private String endereco;
    private String role;
    private List<String> emailsPermitidos;

}
