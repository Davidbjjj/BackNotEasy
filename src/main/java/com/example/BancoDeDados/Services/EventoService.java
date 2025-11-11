package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Exceptions.ResourceNotFoundException;
import com.example.BancoDeDados.Mapper.EventoMapper;
import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EventoService {


    private NotaRepository notaRepository;
    private final EventoRepository eventoRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final EstudanteRepositores estudanteRepository;
    private final NotaEventoRepository notaEventoRepository;
    private final ListaEventoRepository listaEventoRepository;
    private final ListaRepository listaRepository;
    @Autowired
    private EventoMapper eventoMapper;
    @Autowired
    private NotaListaService notaListaService;

    public EventoService(EventoRepository eventoRepository,
                         DisciplinaRepository disciplinaRepository,
                         EstudanteRepositores estudanteRepository,
                         NotaEventoRepository notaEventoRepository,
                         ListaEventoRepository listaEventoRepository,
                         ListaRepository listaRepository) {
        this.eventoRepository = eventoRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.estudanteRepository = estudanteRepository;
        this.notaEventoRepository = notaEventoRepository;
        this.listaEventoRepository=listaEventoRepository;
        this.listaRepository=listaRepository;
    }

    @Transactional
    public EventoComNotasResponse criarEvento(EventoRequest dto) {
        Disciplina disciplina = findDisciplinaByIdOrThrow(dto.getDisciplinaId());
        Professor professor = disciplina.getProfessor();
        if (professor == null) {
            throw new IllegalStateException("Disciplina sem professor associado");
        }

        Evento evento = eventoMapper.toEntity(dto, disciplina, professor);
        Evento eventoSalvo = eventoRepository.save(evento);

        associarTodosEstudantesDaDisciplina(eventoSalvo.getId(), disciplina);

        return eventoMapper.toResponse(eventoSalvo);
    }
    @Transactional
    public void associarTodosEstudantesDaDisciplina(UUID eventoId, Disciplina disciplina) {
        Evento evento = findEventoByIdOrThrow(eventoId);

        List<Estudante> estudantesDaDisciplina = (List<Estudante>) disciplina.getEstudantes();

        if (estudantesDaDisciplina != null && !estudantesDaDisciplina.isEmpty()) {
            for (Estudante estudante : estudantesDaDisciplina) {
                try {
                    boolean jaVinculado = evento.getNotasEstudante().stream()
                            .anyMatch(ne -> ne.getEstudante() != null &&
                                    estudante.getId().equals(ne.getEstudante().getId()));

                    if (!jaVinculado) {
                        NotaEvento notaEvento = new NotaEvento();
                        notaEvento.setEstudante(estudante);
                        notaEvento.setEvento(evento);
                        notaEvento.setProfessor(evento.getProfessor());
                        notaEvento.setNota(null);
                        notaEvento.setObservacao(null);
                        notaEvento.setStatusEntrega(NotaEvento.StatusEntrega.PENDENTE);

                        evento.getNotasEstudante().add(notaEvento);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao associar estudante " + estudante.getId() + ": " + e.getMessage());
                }
            }
            eventoRepository.save(evento);
        }
    }



    @Transactional
    public Evento atualizarEvento(UUID eventoId, EventoComNotasResponse dto) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        Disciplina disciplina = findDisciplinaByIdOrThrow(dto.getId());

        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setNotaMaxima(dto.getNotaMaxima());
        evento.setData(dto.getData());
        evento.setArquivos(
                dto.getNotasEstudantes() == null
                        ? Collections.emptyList()
                        : dto.getNotasEstudantes().stream()
                        .map(NotaEstudanteResponse::getArquivosEntrega)
                        .filter(Objects::nonNull)
                        .flatMap(List::stream)
                        .collect(Collectors.toList())
        );

        evento.setDisciplina(disciplina);

        return eventoRepository.save(evento);
    }

    @Transactional
    public void deletarEvento(UUID eventoId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        eventoRepository.delete(evento);
    }

    @Transactional
    public Evento adicionarEstudante(UUID eventoId, UUID estudanteId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        Estudante estudante = findEstudanteByIdOrThrow(estudanteId);

        boolean jaVinculado = evento.getNotasEstudante().stream()
                .anyMatch(ne -> ne.getEstudante() != null && estudanteId.equals(ne.getEstudante().getId()));
        if (jaVinculado) {
            throw new IllegalStateException("Estudante já está vinculado a este evento");
        }

        NotaEvento notaEvento = new NotaEvento();
        notaEvento.setEstudante(estudante);
        notaEvento.setEvento(evento);
        notaEvento.setProfessor(evento.getProfessor());
        notaEvento.setNota(null);
        notaEvento.setObservacao(null);

        evento.getNotasEstudante().add(notaEvento);
        return eventoRepository.save(evento);
    }

    @Transactional
    public Evento removerEstudante(UUID eventoId, UUID estudanteId) {
        Evento evento = findEventoByIdOrThrow(eventoId);

        NotaEvento notaEvento = evento.getNotasEstudante().stream()
                .filter(ne -> ne.getEstudante() != null && estudanteId.equals(ne.getEstudante().getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Nota do estudante não encontrada nesse evento"));

        evento.getNotasEstudante().remove(notaEvento);
        notaEventoRepository.delete(notaEvento);

        return eventoRepository.save(evento);
    }

    @Transactional
    public NotaEventoResponse atualizarEntregaEstudante(UUID eventoId, UUID estudanteId, EntregaEstudanteRequest request) {
        Estudante estudante = findEstudanteByIdOrThrow(estudanteId);
        Evento evento = findEventoByIdOrThrow(eventoId);

        NotaEvento notaEvento = notaEventoRepository.findByEstudanteAndEvento(estudante, evento)
                .orElseThrow(() -> new ResourceNotFoundException("Relação estudante-evento não encontrada"));

        NotaEvento.StatusEntrega novoStatus;
        try {
            novoStatus = NotaEvento.StatusEntrega.valueOf(request.getStatusEntrega().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("StatusEntrega inválido: " + request.getStatusEntrega());
        }
        notaEvento.setStatusEntrega(novoStatus);

        if (novoStatus == NotaEvento.StatusEntrega.ENTREGUE) {
            notaEvento.setComentarioEntrega(request.getComentarioEntrega());
            notaEvento.setArquivosEntrega(Optional.ofNullable(request.getArquivosEntrega()).orElse(Collections.emptyList()));
        } else {
            notaEvento.setComentarioEntrega(null);
            notaEvento.setArquivosEntrega(Collections.emptyList());
        }

        NotaEvento salvo = notaEventoRepository.save(notaEvento);
        return convertToResponse(salvo);
    }

    @Transactional
    public NotaEventoResponse avaliarEntrega(UUID eventoId, UUID estudanteId, AvaliacaoProfessorRequest request) {
        Estudante estudante = findEstudanteByIdOrThrow(estudanteId);
        Evento evento = findEventoByIdOrThrow(eventoId);

        NotaEvento notaEvento = notaEventoRepository.findByEstudanteAndEvento(estudante, evento)
                .orElseThrow(() -> new ResourceNotFoundException("Relação estudante-evento não encontrada"));

        notaEvento.setNota(request.getNota());
        notaEvento.setObservacao(request.getObservacao());

        NotaEvento salvo = notaEventoRepository.save(notaEvento);
        return convertToResponse(salvo);
    }

    @Transactional
    public NotaEventoResponse salvarNotaEvento(NotaEventoRequest request) {
        AvaliacaoProfessorRequest avaliacaoRequest = new AvaliacaoProfessorRequest(
                request.getNota(),
                request.getObservacao()
        );
        return this.avaliarEntrega(request.getEventoId(), request.getEstudanteId(), avaliacaoRequest);
    }

    @Transactional(readOnly = true)
    public EventoDetalhesResponse buscarPorId(UUID eventoId) {
        Evento evento = findEventoByIdOrThrow(eventoId);

        ProfessorSimplesDTO professorDTO = (evento.getProfessor() != null)
                ? new ProfessorSimplesDTO(evento.getProfessor().getId(), evento.getProfessor().getNome())
                : null;

        DisciplinaSimplesDTO disciplinaDTO = (evento.getDisciplina() != null)
                ? new DisciplinaSimplesDTO(evento.getDisciplina().getId(), evento.getDisciplina().getNome())
                : null;

        List<NotaAlunoSimplesResponse> notasDTO = evento.getNotasEstudante().stream()
                .filter(nota -> nota.getEstudante() != null && nota.getNota() != null)
                .map(nota -> new NotaAlunoSimplesResponse(
                        nota.getEstudante().getId(),
                        nota.getEstudante().getNome(),
                        nota.getNota()
                ))
                .collect(Collectors.toList());

        List<ListaSimplesResponse> listasDTO = listaEventoRepository.findByEvento(evento).stream()
                .map(listaEvento -> new ListaSimplesResponse(
                        listaEvento.getLista().getId(),
                        listaEvento.getLista().getTitulo()
                ))
                .collect(Collectors.toList());

        return new EventoDetalhesResponse(
                evento.getId(),
                evento.getTitulo(),
                evento.getDescricao(),
                evento.getData() != null ? evento.getData().toLocalDate() : null,
                disciplinaDTO,
                professorDTO,
                notasDTO,
                listasDTO
        );
    }

    @Transactional(readOnly = true)
    public List<EventoListaDTO> listarTodosSimplificado() {
        List<Evento> eventos = eventoRepository.findAll();
        return eventos.stream()
                .map(evento -> new EventoListaDTO(
                        evento.getId(),
                        evento.getTitulo(),
                        evento.getData() != null ? evento.getData().toLocalDate() : null,
                        evento.getDisciplina() != null ? evento.getDisciplina().getNome() : null,
                        evento.getNotaMaxima()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Evento> listarPorDisciplina(UUID disciplinaId) {
        return eventoRepository.findByDisciplinaId(disciplinaId);
    }

    @Transactional(readOnly = true)
    public List<EventoComNotasResponse> listarPorProfessor(UUID professorId) {
        List<Evento> eventos = eventoRepository.findByProfessorId(professorId);
        return eventos.stream()
                .map(eventoMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Evento> listarPorEstudanteEmail(String estudanteEmail) {
        Estudante estudante = estudanteRepository.findByEmail(estudanteEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Estudante não encontrado com email: " + estudanteEmail));
        return eventoRepository.findByNotasEstudante_Estudante_Id(estudante.getId());
    }

    @Transactional(readOnly = true)
    public List<Evento> buscarEventosPorDataEDisciplina(LocalDate data, UUID disciplinaId) {
        return eventoRepository.findByDataBetweenAndDisciplina_Id(
                data.atStartOfDay(),
                data.plusDays(1).atStartOfDay(),
                disciplinaId
        );
    }

    @Transactional(readOnly = true)
    public List<Estudante> listarEstudantesDoEvento(UUID eventoId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        return evento.getNotasEstudante().stream()
                .map(NotaEvento::getEstudante)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
    }

    @Transactional(readOnly = true)
    public List<NotaEvento> listarNotasPorEvento(UUID eventoId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        return notaEventoRepository.findByEvento(evento);
    }

    private Evento buildEventoFromDTO(EventoRequest dto, Disciplina disciplina, Professor professor) {
        Evento evento = new Evento();
        evento.setTitulo(dto.getTitulo());
        evento.setDescricao(dto.getDescricao());
        evento.setNotaMaxima(dto.getNotaMaxima());
        evento.setData(dto.getData());
        evento.setDisciplina(disciplina);
        evento.setProfessor(professor);

        if (dto.getArquivos() != null) {
            evento.setArquivos(dto.getArquivos());
        }

        return evento;
    }

    public NotaEventoResponse convertToResponse(NotaEvento notaEvento) {
        String professorNome = notaEvento.getProfessor() != null ? notaEvento.getProfessor().getNome() : null;
        UUID eventoId = notaEvento.getEvento() != null ? notaEvento.getEvento().getId() : null;
        Double notaMaxima = notaEvento.getEvento() != null ? notaEvento.getEvento().getNotaMaxima() : null;
        return new NotaEventoResponse(
                notaEvento.getId(),
                notaEvento.getEstudante() != null ? notaEvento.getEstudante().getNome() : null,
                notaEvento.getEstudante() != null ? notaEvento.getEstudante().getId() : null,
                notaEvento.getEvento() != null ? notaEvento.getEvento().getTitulo() : null,
                eventoId,
                notaEvento.getNota(),
                notaMaxima,
                notaEvento.getObservacao(),
                professorNome,
                notaEvento.getStatusEntrega() != null ? notaEvento.getStatusEntrega().name() : null,
                notaEvento.getComentarioEntrega(),
                Optional.ofNullable(notaEvento.getArquivosEntrega()).orElse(Collections.emptyList())
        );
    }
    private EventoComNotasResponse convertToResponse(Evento evento) {
        EventoComNotasResponse response = new EventoComNotasResponse();
        response.setId(evento.getId());
        response.setTitulo(evento.getTitulo());
        response.setDescricao(evento.getDescricao());
        response.setNotaMaxima(evento.getNotaMaxima());
        response.setData(evento.getData());
        response.setDisciplinaId(evento.getDisciplina().getId());
        response.setDisciplinaNome(evento.getDisciplina().getNome());
        response.setNotasEstudantes(new ArrayList<>()); // Inicialmente vazio

        return response;
    }

    private Evento findEventoByIdOrThrow(UUID eventoId) {
        return eventoRepository.findById(eventoId)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado"));
    }

    private Disciplina findDisciplinaByIdOrThrow(UUID disciplinaId) {
        return disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new ResourceNotFoundException("Disciplina não encontrada"));
    }

    private Estudante findEstudanteByIdOrThrow(UUID estudanteId) {
        return estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudante não encontrado"));
    }

    @Transactional
    public ListaEventoResponse adicionarListaAoEvento(UUID eventoId, ListaEventoRequest request) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        Lista lista = findListaByIdOrThrow(request.getListaId());

        boolean jaExiste = listaEventoRepository.existsByListaAndEvento(lista, evento);
        if (jaExiste) {
            throw new IllegalStateException("Evento já está vinculado a esta lista");
        }

        ListaEvento listaEvento = new ListaEvento();
        listaEvento.setLista(lista);
        listaEvento.setEvento(evento);

        ListaEvento salvo = listaEventoRepository.save(listaEvento);
        sincronizarNotasListaParaEvento(lista, evento);
        return convertToListaEventoResponse(salvo);
    }
    @Transactional
    public void sincronizarNotasListaParaEvento(Lista lista, Evento evento) {
        List<Estudante> estudantesDaLista = lista.getEstudantes();

        for (Estudante estudante : estudantesDaLista) {
            try {
                Optional<ListaEstudanteNota> notaListaOpt = notaListaService.buscarNotaEstudanteOptional(lista.getId(), estudante.getId());

                if (notaListaOpt.isPresent()) {
                    ListaEstudanteNota notaLista = notaListaOpt.get();

                    NotaEvento notaEvento = notaEventoRepository.findByEstudanteAndEvento(estudante, evento)
                            .orElseGet(() -> {
                                NotaEvento novaNotaEvento = new NotaEvento();
                                novaNotaEvento.setEstudante(estudante);

                                novaNotaEvento.setEvento(evento);
                                novaNotaEvento.setProfessor(evento.getProfessor());
                                novaNotaEvento.setStatusEntrega(NotaEvento.StatusEntrega.ENTREGUE);
                                return novaNotaEvento;
                            });

                    Double notaConvertida = converterNotaListaParaEvento(
                            notaLista.getNota(),
                            evento.getNotaMaxima()
                    );

                    notaEvento.setNota(notaConvertida);
                    notaEvento.setObservacao("Nota sincronizada automaticamente da lista: " + lista.getTitulo());

                    notaEventoRepository.save(notaEvento);
                }
            } catch (Exception e) {
                System.err.println("Erro ao sincronizar nota do estudante " + estudante.getId() + ": " + e.getMessage());
            }
        }
    }
    private Double converterNotaListaParaEvento(BigDecimal notaLista, Double notaMaximaEvento) {
        if (notaLista == null) {
            return null;
        }

        double notaListaDouble = notaLista.doubleValue();

        if (notaMaximaEvento == 10.0) {
            return notaListaDouble;
        }

        return (notaListaDouble / 10.0) * notaMaximaEvento;
    }

    private Double converterNotaListaParaEvento(Double notaLista, Double notaMaximaEvento) {
        if (notaLista == null) {
            return null;
        }

        if (notaMaximaEvento == 10.0) {
            return notaLista;
        }

        return (notaLista / 10.0) * notaMaximaEvento;
    }

    @Transactional
    public void sincronizarNotasListaEvento(UUID eventoId, UUID listaId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        Lista lista = findListaByIdOrThrow(listaId);

        boolean listaAssociada = listaEventoRepository.existsByListaAndEvento(lista, evento);
        if (!listaAssociada) {
            throw new IllegalStateException("Lista não está associada a este evento");
        }

        sincronizarNotasListaParaEvento(lista, evento);
    }
    @Transactional
    public void removerListaDoEvento(UUID eventoId, UUID listaId) {
        Evento evento = findEventoByIdOrThrow(eventoId);
        Lista lista = findListaByIdOrThrow(listaId);

        ListaEvento listaEvento = listaEventoRepository.findByListaAndEvento(lista, evento)
                .orElseThrow(() -> new ResourceNotFoundException("Relação lista-evento não encontrada"));

        listaEventoRepository.delete(listaEvento);
    }

    private ListaEventoResponse convertToListaEventoResponse(ListaEvento listaEvento) {
        ListaEventoResponse response = new ListaEventoResponse();
        response.setId(listaEvento.getId());
        response.setListaId(listaEvento.getLista().getId());
        response.setListaTitulo(listaEvento.getLista().getTitulo());
        response.setEventoId(listaEvento.getEvento().getId());
        response.setEventoTitulo(listaEvento.getEvento().getTitulo());
        return response;
    }

    private Lista findListaByIdOrThrow(UUID listaId) {
        return listaRepository.findById(listaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lista não encontrada"));
    }
}
