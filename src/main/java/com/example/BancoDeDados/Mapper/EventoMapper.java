package com.example.BancoDeDados.Mapper;


import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.ResponseDTO.EventoComNotasResponse;
import com.example.BancoDeDados.ResponseDTO.EventoRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class EventoMapper {

    public EventoComNotasResponse toResponse(Evento evento) {
        EventoComNotasResponse response = new EventoComNotasResponse();
        response.setId(evento.getId());
        response.setTitulo(evento.getTitulo());
        response.setDescricao(evento.getDescricao());
        response.setNotaMaxima(evento.getNotaMaxima());
        response.setData(evento.getData());
        response.setDisciplinaId(evento.getDisciplina().getId());
        response.setDisciplinaNome(evento.getDisciplina().getNome());
        response.setNotasEstudantes(new ArrayList<>());

        return response;
    }

    public Evento toEntity(EventoRequest dto, Disciplina disciplina, Professor professor) {
        Evento evento = new Evento();
        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setNotaMaxima(dto.getNotaMaxima());
        evento.setData(dto.getData());
        evento.setArquivos(dto.getArquivos());
        evento.setDisciplina(disciplina);
        evento.setProfessor(professor);

        return evento;
    }
}
