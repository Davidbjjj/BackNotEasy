package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
//import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.DisciplinaRequestDTO;
import com.example.BancoDeDados.ResponseDTO.DisciplinaResponseDTO;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DisciplinaService {

    private final DisciplinaRepository disciplinaRepository;
    private final ProfessorRepositores professorRepository;
    private final InstituicaoRepository instituicaoRepository;
    private final EstudanteRepositores estudanteRepository;

    public DisciplinaService(DisciplinaRepository disciplinaRepository,
                             ProfessorRepositores professorRepository,
                             InstituicaoRepository instituicaoRepository,
                             EstudanteRepositores estudanteRepository) {
        this.disciplinaRepository = disciplinaRepository;
        this.professorRepository = professorRepository;
        this.instituicaoRepository = instituicaoRepository;
        this.estudanteRepository = estudanteRepository;
    }

    @Transactional
    public Disciplina criar(DisciplinaRequestDTO dto) {
        try {
            Instituicao instituicao = instituicaoRepository.findById(dto.instituicaoId())
                    .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

            // Use o novo método que já faz a verificação da instituição
            Professor professor = professorRepository.findByEmailAndInstituicaoId(dto.emailProfessor(), dto.instituicaoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Professor não encontrado ou não pertence à instituição informada"));

            Disciplina disciplina = new Disciplina();
            disciplina.setNome(dto.nome());
            disciplina.setInstituicao(instituicao);
            disciplina.setProfessor(professor);

            return disciplinaRepository.save(disciplina);

        } catch (Exception e) {
            throw new IllegalArgumentException("Erro ao criar disciplina: " + e.getMessage());
        }
    }

    public List<DisciplinaResponseDTO> listar() {
        return disciplinaRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    public Optional<DisciplinaResponseDTO> buscarPorId(UUID id) {
        return disciplinaRepository.findById(id).map(this::mapToDTO);
    }

    public List<DisciplinaResponseDTO> buscarPorInstituicao(String nomeInstituicao) {
        return disciplinaRepository.findByInstituicao_Nome(nomeInstituicao)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public void deletar(UUID id) {
        Disciplina disciplina = disciplinaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));
        disciplina.getEstudante().clear();
        disciplinaRepository.delete(disciplina);
    }

    private DisciplinaResponseDTO mapToDTO(Disciplina disciplina) {
        return new DisciplinaResponseDTO(
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getProfessor().getNome(),
                disciplina.getInstituicao().getNome(),
                disciplina.getEstudante().stream().map(Estudante::getNome).collect(Collectors.toList())
        );
    }
}
