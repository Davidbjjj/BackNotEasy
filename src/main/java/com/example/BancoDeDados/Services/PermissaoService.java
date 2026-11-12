package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.PapelPermissoes;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Repositores.PapelPermissoesRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Security.CallerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

import static com.example.BancoDeDados.Model.GestaoInstitucional.*;

/**
 * FE-306: matriz de papéis × permissões. O INSTITUICAO é o ADMIN (sempre tudo). COORDENADOR e PROFESSOR têm
 * padrões do sistema que o admin pode editar, exceto as permissões reservadas ao admin.
 */
@Service
public class PermissaoService {

    public record Permissao(String chave, String titulo, boolean somenteAdmin) {}

    public record Contexto(UUID instituicaoId, String papel) {}

    public record Matriz(List<String> papeis, List<Permissao> permissoes, Map<String, List<String>> matriz) {}

    public static final List<Permissao> TODAS = List.of(
            new Permissao(VER_DASHBOARD, "Ver o dashboard executivo", false),
            new Permissao(VER_RELATORIOS, "Ver relatórios comparativos", false),
            new Permissao(IMPORTAR_USUARIOS, "Importar alunos e professores (CSV)", false),
            new Permissao(GERIR_CONVITES, "Convidar e gerenciar convites", false),
            new Permissao(GERIR_PERIODOS, "Gerenciar períodos, turmas e arquivamento", false),
            new Permissao(EMITIR_BOLETINS, "Emitir boletins e relatórios externos", false),
            new Permissao(EDITAR_CONFIGURACOES, "Editar as configurações da instituição", false),
            new Permissao(CORRIGIR, "Corrigir respostas abertas", false),
            new Permissao(BANCO_QUESTOES, "Usar o banco de questões", false),
            new Permissao(VER_ALUNOS_RISCO, "Ver alunos em risco", false),
            new Permissao(EXPORTAR_NOTAS, "Exportar notas", false),
            new Permissao(GERIR_PAPEIS, "Editar papéis e permissões", true),
            new Permissao(VER_AUDITORIA, "Ver o log de auditoria", true));

    public static final List<String> PAPEIS = List.of(ADMIN, COORDENADOR, PROFESSOR);

    private static final Set<String> RESERVADAS = TODAS.stream().filter(Permissao::somenteAdmin)
            .map(Permissao::chave).collect(Collectors.toSet());
    private static final Set<String> VALIDAS = TODAS.stream().map(Permissao::chave).collect(Collectors.toSet());

    private static final Map<String, Set<String>> PADRAO = Map.of(
            ADMIN, VALIDAS,
            COORDENADOR, Set.of(VER_DASHBOARD, VER_RELATORIOS, GERIR_CONVITES, GERIR_PERIODOS, EMITIR_BOLETINS,
                    CORRIGIR, BANCO_QUESTOES, VER_ALUNOS_RISCO, EXPORTAR_NOTAS),
            PROFESSOR, Set.of(CORRIGIR, BANCO_QUESTOES, VER_ALUNOS_RISCO, EXPORTAR_NOTAS));

    @Autowired
    private PapelPermissoesRepository repository;
    @Autowired
    private ProfessorRepositores professorRepository;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    /** Quem está chamando: instituição (ADMIN), professor/coordenador ou admin do sistema. */
    public Contexto contexto() {
        if (!CallerContext.isAuthenticated()) throw erro(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        UUID inst = CallerContext.instituicaoIdOuNull();
        if (inst != null) return new Contexto(inst, ADMIN);
        if (CallerContext.isAdmin()) return new Contexto(null, ADMIN);
        if (CallerContext.isProfessor() && CallerContext.userId() != null) {
            Professor p = professorRepository.findById(CallerContext.userId())
                    .orElseThrow(() -> erro(HttpStatus.FORBIDDEN, "Professor não encontrado"));
            String papel = COORDENADOR.equals(p.getPapelInstituicao()) ? COORDENADOR : PROFESSOR;
            return new Contexto(p.getInstituicao() == null ? null : p.getInstituicao().getId(), papel);
        }
        throw erro(HttpStatus.FORBIDDEN, "Acesso restrito à equipe da instituição");
    }

    public Set<String> permissoesDo(UUID instituicaoId, String papel) {
        if (ADMIN.equals(papel)) return VALIDAS;
        Set<String> definidas = instituicaoId == null ? null
                : repository.findByInstituicaoIdAndPapel(instituicaoId, papel)
                .map(p -> Arrays.stream(p.getPermissoes().split(",")).filter(s -> !s.isBlank()).collect(Collectors.toSet()))
                .orElse(null);
        Set<String> base = definidas != null ? definidas : PADRAO.getOrDefault(papel, Set.of());
        return base.stream().filter(c -> VALIDAS.contains(c) && !RESERVADAS.contains(c)).collect(Collectors.toSet());
    }

    /** 403 se o chamador não tem a permissão. Devolve o contexto. */
    public Contexto exigir(String permissao) {
        Contexto c = contexto();
        if (!permissoesDo(c.instituicaoId(), c.papel()).contains(permissao)) {
            throw erro(HttpStatus.FORBIDDEN, "Você não tem permissão para isso (" + permissao + ")");
        }
        return c;
    }

    /** Como {@link #exigir}, e ainda exige que o chamador pertença a uma instituição. */
    public UUID exigirInstituicao(String permissao) {
        Contexto c = exigir(permissao);
        if (c.instituicaoId() == null) throw erro(HttpStatus.FORBIDDEN, "Esta ação é da equipe de uma instituição");
        return c.instituicaoId();
    }

    public List<String> minhas() {
        Contexto c = contexto();
        return TODAS.stream().map(Permissao::chave).filter(permissoesDo(c.instituicaoId(), c.papel())::contains)
                .collect(Collectors.toList());
    }

    public Matriz matriz(UUID instituicaoId) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (String papel : PAPEIS) {
            Set<String> set = permissoesDo(instituicaoId, papel);
            m.put(papel, TODAS.stream().map(Permissao::chave).filter(set::contains).collect(Collectors.toList()));
        }
        return new Matriz(PAPEIS, TODAS, m);
    }

    @Transactional
    public Matriz salvar(UUID instituicaoId, String papel, List<String> permissoes) {
        if (!COORDENADOR.equals(papel) && !PROFESSOR.equals(papel)) {
            throw erro(HttpStatus.BAD_REQUEST, "Só os papéis COORDENADOR e PROFESSOR podem ser editados");
        }
        Set<String> novas = new LinkedHashSet<>();
        for (String p : permissoes == null ? List.<String>of() : permissoes) {
            if (!VALIDAS.contains(p)) throw erro(HttpStatus.BAD_REQUEST, "Permissão desconhecida: " + p);
            if (RESERVADAS.contains(p)) throw erro(HttpStatus.BAD_REQUEST, "A permissão " + p + " é só do admin");
            novas.add(p);
        }
        PapelPermissoes linha = repository.findByInstituicaoIdAndPapel(instituicaoId, papel).orElseGet(() -> {
            PapelPermissoes n = new PapelPermissoes();
            n.setInstituicaoId(instituicaoId);
            n.setPapel(papel);
            return n;
        });
        linha.setPermissoes(String.join(",", novas));
        repository.save(linha);
        return matriz(instituicaoId);
    }
}
