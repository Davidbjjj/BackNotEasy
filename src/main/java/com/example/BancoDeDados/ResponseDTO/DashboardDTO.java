package com.example.BancoDeDados.ResponseDTO;   

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;

import java.util.List;
import java.util.stream.Collectors;

public class DashboardDTO {

    private String tituloLista;
    private String nomeProfessor;
    private List<String> estudantes;
    private List<String> questoes;

    public DashboardDTO(Lista lista) {
        this.tituloLista = lista.getTitulo();
        this.nomeProfessor = lista.getProfessor().getNome();
        this.estudantes = lista.getEstudantes()
                .stream()
                .map(Estudante::getNome)
                .collect(Collectors.toList());
        this.questoes = lista.getQuestoes()
                .stream()
                .map(Questao::getEnunciado)
                .collect(Collectors.toList());
    }

    // Getters e Setters
}
