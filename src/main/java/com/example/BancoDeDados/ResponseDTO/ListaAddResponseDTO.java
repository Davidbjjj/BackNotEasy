package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Questao;

import java.util.List;
import java.util.UUID;

public record ListaAddResponseDTO (UUID listaId, List<Questao> questoes){
}
