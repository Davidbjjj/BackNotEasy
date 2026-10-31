package com.example.BancoDeDados.ResponseDTO;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.UUID;

public class EventoStatusResponse {

    @JsonProperty("idEvento")
    private UUID idEvento;

    @JsonProperty("nomeEvento")
    private String nomeEvento;

    @JsonProperty("prazo")
    private LocalDate prazo;

    @JsonProperty("descrição")
    private String descricao;

    @JsonProperty("nomeDisciplina")
    private String nomeDisciplina;

    @JsonProperty("nota do aluno")
    private String notaDoAluno;

    @JsonProperty("status")
    private String status;

    public UUID getIdEvento() { return idEvento; }
    public void setIdEvento(UUID idEvento) { this.idEvento = idEvento; }

    public String getNomeEvento() { return nomeEvento; }
    public void setNomeEvento(String nomeEvento) { this.nomeEvento = nomeEvento; }

    public LocalDate getPrazo() { return prazo; }
    public void setPrazo(LocalDate prazo) { this.prazo = prazo; }

    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public String getNomeDisciplina() { return nomeDisciplina; }
    public void setNomeDisciplina(String nomeDisciplina) { this.nomeDisciplina = nomeDisciplina; }

    public String getNotaDoAluno() { return notaDoAluno; }
    public void setNotaDoAluno(String notaDoAluno) { this.notaDoAluno = notaDoAluno; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

