package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.AvisoRepository;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Security.CallerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** FE-209: mural de avisos do professor para a turma (disciplina). */
@Service
public class AvisoService {

    static final long MAX_ANEXO = 5L * 1024 * 1024;
    private static final Set<String> TIPOS = Set.of("application/pdf", "image/png", "image/jpeg", "text/plain");

    public record AnexoDTO(String nome, String tipo, String url) {}

    public record AvisoDTO(UUID id, UUID disciplinaId, String titulo, String texto, boolean fixado, Instant criadoEm,
                           String autor, AnexoDTO anexo) {}

    public record Anexo(String nome, String tipo, byte[] dados) {}

    @Autowired
    private AvisoRepository avisoRepository;
    @Autowired
    private DisciplinaRepository disciplinaRepository;
    @Autowired
    private ProfessorRepositores professorRepository;
    @Autowired
    private EstudanteRepositores estudanteRepository;
    @Autowired
    private NotificacaoService notificacaoService;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    private Disciplina disciplina(UUID id) {
        return disciplinaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Disciplina não encontrada"));
    }

    private static boolean ehDono(Disciplina d) {
        UUID uid = CallerContext.userId();
        if (CallerContext.isAdmin()) return true;
        if (CallerContext.isProfessor()) return d.getProfessor() != null && d.getProfessor().getId().equals(uid);
        if (CallerContext.instituicaoIdOuNull() != null) {
            return d.getInstituicao() != null && d.getInstituicao().getId().equals(uid);
        }
        return false;
    }

    private static boolean matriculado(Disciplina d) {
        UUID uid = CallerContext.userId();
        return CallerContext.isAluno() && uid != null && d.getEstudantes().stream().anyMatch(e -> e.getId().equals(uid));
    }

    private void exigirAcesso(Disciplina d) {
        if (!CallerContext.isAuthenticated()) throw erro(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        if (!ehDono(d) && !matriculado(d)) throw erro(HttpStatus.FORBIDDEN, "Você não tem acesso ao mural desta disciplina");
    }

    private void exigirDono(Disciplina d) {
        if (!CallerContext.isAuthenticated()) throw erro(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        if (!ehDono(d)) throw erro(HttpStatus.FORBIDDEN, "Só o professor da disciplina pode alterar o mural");
    }

    private static AvisoDTO paraDTO(Aviso a) {
        AnexoDTO anexo = a.getAnexoNome() == null ? null
                : new AnexoDTO(a.getAnexoNome(), a.getAnexoTipo(), "/avisos/" + a.getId() + "/anexo");
        return new AvisoDTO(a.getId(), a.getDisciplina().getId(), a.getTitulo(), a.getTexto(), a.isFixado(),
                a.getCriadoEm(), a.getProfessor() == null ? null : a.getProfessor().getNome(), anexo);
    }

    @Transactional(readOnly = true)
    public List<AvisoDTO> listar(UUID disciplinaId) {
        Disciplina d = disciplina(disciplinaId);
        exigirAcesso(d);
        return avisoRepository.findByDisciplinaIdOrderByFixadoDescCriadoEmDesc(disciplinaId).stream()
                .map(AvisoService::paraDTO).collect(Collectors.toList());
    }

    @Transactional
    public AvisoDTO criar(UUID disciplinaId, String titulo, String texto, boolean fixado, MultipartFile anexo) {
        Disciplina d = disciplina(disciplinaId);
        exigirDono(d);
        d.exigirAtiva();
        if (titulo == null || titulo.isBlank() || titulo.length() > 150) {
            throw erro(HttpStatus.BAD_REQUEST, "O título é obrigatório e pode ter até 150 caracteres");
        }
        if (texto == null || texto.isBlank() || texto.length() > 5000) {
            throw erro(HttpStatus.BAD_REQUEST, "O texto é obrigatório e pode ter até 5000 caracteres");
        }
        Aviso a = new Aviso();
        a.setDisciplina(d);
        a.setProfessor(CallerContext.isProfessor() ? professorRepository.findById(CallerContext.userId()).orElse(d.getProfessor())
                : d.getProfessor());
        a.setTitulo(titulo.trim());
        a.setTexto(texto.trim());
        a.setFixado(fixado);
        if (anexo != null && !anexo.isEmpty()) {
            if (anexo.getSize() > MAX_ANEXO) throw erro(HttpStatus.BAD_REQUEST, "O anexo pode ter no máximo 5 MB");
            String tipo = anexo.getContentType() == null ? "" : anexo.getContentType().toLowerCase();
            if (!TIPOS.contains(tipo)) throw erro(HttpStatus.BAD_REQUEST, "Anexo inválido: use PDF, PNG, JPG ou TXT");
            try {
                a.setAnexoDados(anexo.getBytes());
            } catch (IOException e) {
                throw erro(HttpStatus.BAD_REQUEST, "Não foi possível ler o anexo");
            }
            String nome = anexo.getOriginalFilename() == null ? "anexo" : anexo.getOriginalFilename().replaceAll("[\\\\/]", "_");
            a.setAnexoNome(nome.length() > 200 ? nome.substring(0, 200) : nome);
            a.setAnexoTipo(tipo);
        }
        Aviso salvo = avisoRepository.save(a);

        String msg = "Aviso em " + d.getNome() + ": " + salvo.getTitulo();
        for (Estudante e : d.getEstudantes()) {
            notificacaoService.criar(e.getId(), TipoNotificacao.GERAL, "Novo aviso", msg, "/disciplinas/" + d.getId());
        }
        return paraDTO(salvo);
    }

    private Aviso aviso(UUID id) {
        return avisoRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Aviso não encontrado"));
    }

    @Transactional
    public AvisoDTO fixar(UUID id, boolean fixado) {
        Aviso a = aviso(id);
        exigirDono(a.getDisciplina());
        a.setFixado(fixado);
        return paraDTO(avisoRepository.save(a));
    }

    @Transactional
    public void excluir(UUID id) {
        Aviso a = aviso(id);
        exigirDono(a.getDisciplina());
        avisoRepository.delete(a);
    }

    @Transactional(readOnly = true)
    public Anexo anexo(UUID id) {
        Aviso a = aviso(id);
        exigirAcesso(a.getDisciplina());
        if (a.getAnexoDados() == null) throw erro(HttpStatus.NOT_FOUND, "Este aviso não tem anexo");
        return new Anexo(a.getAnexoNome(), a.getAnexoTipo(), a.getAnexoDados());
    }
}
