package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
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
    private final RespostaEstudantesRepository respostaEstudantesRepository;

    public DisciplinaService(DisciplinaRepository disciplinaRepository,
                             ProfessorRepositores professorRepository,
                             InstituicaoRepository instituicaoRepository,
                             EstudanteRepositores estudanteRepository,
                             ListaRepository listaRepository,
                             ListaService listaService,
                             RespostaEstudantesRepository respostaEstudantesRepository) {
        this.disciplinaRepository = disciplinaRepository;
        this.professorRepository = professorRepository;
        this.instituicaoRepository = instituicaoRepository;
        this.estudanteRepository = estudanteRepository;
        this.listaRepository=listaRepository;
        this.listaService=listaService;
        this.respostaEstudantesRepository = respostaEstudantesRepository;
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
    public List<EstudanteSimplesDTO> listarEstudantes(UUID disciplinaId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        return disciplina.getEstudantes().stream()
                .map(e -> new EstudanteSimplesDTO(
                        e.getId(),
                        e.getNome(),
                        e.getEmail(),
                        e.getDataNascimento(),
                        e.getInstituicao()
                ))
                .collect(Collectors.toList());
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

    // New: retorna média por estudante na disciplina
    public List<AlunoMediaDTO> getAlunoMedias(UUID disciplinaId, UUID professorId) {
        // valida disciplina e pertencimento ao professor
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));
        if (disciplina.getProfessor() == null || !disciplina.getProfessor().getId().equals(professorId)) {
            throw new IllegalArgumentException("Acesso negado: professor não é proprietário da disciplina");
        }

        List<AlunoMediaDTO> medias = respostaEstudantesRepository.findAlunoMediasByDisciplina(disciplinaId);
        // converter média (0..1) para 0..100 e manter contagem
        return medias.stream().map(m -> {
            if (m.getMedia() != null) {
                m.setMedia(Math.round(m.getMedia() * 10000.0) / 100.0); // 2 casas decimais
            }
            return m;
        }).collect(Collectors.toList());
    }

    // New: listas com menores médias
    public List<ListaMediaDTO> getListasMenoresMedias(UUID disciplinaId, UUID professorId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));
        if (disciplina.getProfessor() == null || !disciplina.getProfessor().getId().equals(professorId)) {
            throw new IllegalArgumentException("Acesso negado: professor não é proprietário da disciplina");
        }

        List<ListaMediaDTO> listas = respostaEstudantesRepository.findListaMediasByDisciplinaOrderByMediaAsc(disciplinaId);
        return listas.stream().map(l -> {
            if (l.getMedia() != null) {
                l.setMedia(Math.round(l.getMedia() * 10000.0) / 100.0);
            }
            return l;
        }).collect(Collectors.toList());
    }

    // New: atividades concluidas por estudante na disciplina
    public List<AtividadeConcluidaDTO> getAtividadesConcluidas(UUID disciplinaId, UUID professorId) {
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));
        if (disciplina.getProfessor() == null || !disciplina.getProfessor().getId().equals(professorId)) {
            throw new IllegalArgumentException("Acesso negado: professor não é proprietário da disciplina");
        }

        return respostaEstudantesRepository.findAtividadesConcluidasByDisciplina(disciplinaId);
    }

    // New: listar todas as atividades (listas) de uma disciplina
    public List<ListaAtividadeDTO> listarAtividades(UUID disciplinaId) {
        // Verifica se a disciplina existe
        disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina não encontrada"));

        List<Lista> listas = listaRepository.findByDisciplinaId(disciplinaId);

        return listas.stream()
                .map(lista -> {
                    // Busca o eventoId associado à lista (se houver)
                    UUID eventoId = null;
                    if (lista.getEventos() != null && !lista.getEventos().isEmpty()) {
                        // Pega o primeiro evento associado (normalmente há apenas um)
                        eventoId = lista.getEventos().get(0).getEvento().getId();
                    }

                    return new ListaAtividadeDTO(
                            eventoId,           // ID do evento (pode ser null)
                            lista.getId(),      // ID da lista
                            lista.getTitulo(),
                            lista.getQuestoes() != null ? lista.getQuestoes().size() : 0
                    );
                })
                .collect(Collectors.toList());
    }
}
