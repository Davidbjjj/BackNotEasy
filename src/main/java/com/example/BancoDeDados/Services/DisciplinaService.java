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
    private final ListaRepository listaRepository;
    private final ListaService listaService;

    public DisciplinaService(DisciplinaRepository disciplinaRepository,
                             ProfessorRepositores professorRepository,
                             InstituicaoRepository instituicaoRepository,
                             EstudanteRepositores estudanteRepository,
                             ListaRepository listaRepository,
                             ListaService listaService) {
        this.disciplinaRepository = disciplinaRepository;
        this.professorRepository = professorRepository;
        this.instituicaoRepository = instituicaoRepository;
        this.estudanteRepository = estudanteRepository;
        this.listaRepository=listaRepository;
        this.listaService=listaService;
    }

    @Transactional
    public DisciplinaResponseDTO adicionarEstudantee(UUID disciplinaId, UUID estudanteId) {
        System.out.println("=== ADICIONAR ESTUDANTE ===");
        System.out.println("Disciplina ID: " + disciplinaId);
        System.out.println("Estudante ID: " + estudanteId);

        try {
            Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                    .orElseThrow(() -> {
                        System.out.println("Disciplina não encontrada: " + disciplinaId);
                        return new IllegalArgumentException("Disciplina não encontrada");
                    });

            Estudante estudante = estudanteRepository.findById(estudanteId)
                    .orElseThrow(() -> {
                        System.out.println("Estudante não encontrado: " + estudanteId);
                        return new IllegalArgumentException("Estudante não encontrado");
                    });

            System.out.println("Disciplina encontrada: " + disciplina.getNome());
            System.out.println("Estudante encontrado: " + estudante.getNome());

            // Verifica se o estudante já está na disciplina
            if (disciplina.getEstudantes().contains(estudante)) {
                System.out.println("Estudante já está na disciplina");
                throw new IllegalArgumentException("Estudante já está na disciplina");
            }

            // Adiciona o estudante à disciplina
            disciplina.getEstudantes().add(estudante);
            Disciplina disciplinaSalva = disciplinaRepository.save(disciplina);

            System.out.println("Estudante adicionado com sucesso!");
            System.out.println("Total de estudantes na disciplina: " + disciplinaSalva.getEstudantes().size());

            return mapToDTO(disciplinaSalva);

        } catch (Exception e) {
            System.out.println("ERRO ao adicionar estudante: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    @Transactional
    public DisciplinaResponseDTO adicionarEstudante(UUID disciplinaId, UUID estudanteId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudante não encontrado"));

        // Verifica se o estudante já está na disciplina
        if (disciplina.getEstudantes().contains(estudante)) {
            throw new IllegalArgumentException("Estudante já está na disciplina");
        }

        // Adiciona o estudante à disciplina
        disciplina.getEstudantes().add(estudante);
        disciplinaRepository.save(disciplina);

        return mapToDTO(disciplina);
    }
    @Transactional
    public DisciplinaResponseDTO associarLista(UUID disciplinaId, UUID listaId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new IllegalArgumentException("Lista não encontrada"));

        // Verifica se a lista já está associada à disciplina (se você tiver essa relação)
        // Se você quiser controlar essa associação, pode adicionar um campo na entidade Disciplina ou Lista

        // Adiciona todos os estudantes da disciplina à lista
        for (Estudante estudante : disciplina.getEstudantes()) {
            try {
                listaService.adicionarEstudante(listaId, estudante.getId());
            } catch (Exception e) {
                // Loga o erro mas continua com os próximos estudantes
                System.err.println("Erro ao adicionar estudante " + estudante.getNome() + " à lista: " + e.getMessage());
            }
        }

        return mapToDTO(disciplina);
    }

    @Transactional
    public DisciplinaResponseDTO removerEstudante(UUID disciplinaId, UUID estudanteId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new IllegalArgumentException("Estudante não encontrado"));

        // Remove o estudante da disciplina
        disciplina.getEstudantes().remove(estudante);
        disciplinaRepository.save(disciplina);

        return mapToDTO(disciplina);
    }
    public List<Estudante> listarEstudantes(UUID disciplinaId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        return disciplina.getEstudantes().stream().collect(Collectors.toList());
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

    public List<DisciplinaResponseDTO> buscarPorProfessor(UUID professorId) {
        return disciplinaRepository.findByProfessorId(professorId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }
    
    public List<DisciplinaResponseDTO> buscarPorEstudante(UUID estudanteId) {
        return disciplinaRepository.findByEstudantesId(estudanteId)
                .stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Transactional
    public void deletar(UUID id) {
        Disciplina disciplina = disciplinaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));
        disciplina.getEstudantes().clear();
        disciplinaRepository.delete(disciplina);
    }

    private DisciplinaResponseDTO mapToDTO(Disciplina disciplina) {
        return new DisciplinaResponseDTO(
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getProfessor().getNome(),
                disciplina.getInstituicao().getNome(),
                disciplina.getEstudantes().stream().map(Estudante::getNome).collect(Collectors.toList())
        );
    }
}
