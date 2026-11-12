package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** FE-302: convite por e-mail → aceite com criação de senha → vínculo com as disciplinas. */
@Service
public class ConviteService {

    public static final int VALIDADE_DIAS = 7;
    public static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public record DisciplinaRef(UUID id, String nome) {}

    public record CriarRequest(String email, String nome, String papel, List<UUID> disciplinaIds) {}

    public record ConviteDTO(UUID id, String email, String nome, String papel, List<DisciplinaRef> disciplinas,
                             String status, Instant criadoEm, Instant expiraEm, Instant aceitoEm, String link) {}

    public record Info(String instituicao, String email, String nome, String papel, List<String> disciplinas,
                       String status, boolean contaPreCriada) {}

    public record AceiteRequest(String nome, String senha, LocalDate dataNascimento) {}

    /** Convite recém-emitido com o token em claro (só existe aqui, nunca é guardado). */
    public record Emitido(Convite convite, String token) {}

    @Autowired private ConviteRepository repository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ProfessorRepositores professorRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private EmailService emailService;
    @Autowired private NotificacaoService notificacaoService;
    @Autowired private AuditoriaService auditoria;

    @Value("${app.front-url:http://localhost:3000}")
    private String frontUrl;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String novoToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static boolean senhaForte(String s) {
        return s != null && s.length() >= 8 && s.chars().anyMatch(Character::isUpperCase)
                && s.chars().anyMatch(Character::isLowerCase) && s.chars().anyMatch(Character::isDigit);
    }

    public String link(String token) {
        return frontUrl.replaceAll("/+$", "") + "/convite/" + token;
    }

    private List<DisciplinaRef> disciplinas(Convite c) {
        List<DisciplinaRef> out = new ArrayList<>();
        for (String id : c.getDisciplinaIds() == null ? new String[0] : c.getDisciplinaIds().split(",")) {
            if (id.isBlank()) continue;
            disciplinaRepository.findById(UUID.fromString(id.trim())).ifPresent(d -> out.add(new DisciplinaRef(d.getId(), d.getNome())));
        }
        return out;
    }

    private ConviteDTO dto(Convite c, String token) {
        return new ConviteDTO(c.getId(), c.getEmail(), c.getNome(), c.getPapel(), disciplinas(c), c.statusEfetivo(),
                c.getCriadoEm(), c.getExpiraEm(), c.getAceitoEm(), token == null ? null : link(token));
    }

    /** Valida e-mail, papel e disciplinas da instituição; devolve as disciplinas. */
    private List<Disciplina> validar(Instituicao inst, String email, String papel, List<UUID> disciplinaIds) {
        if (email == null || !EMAIL.matcher(email.trim()).matches()) throw erro(HttpStatus.BAD_REQUEST, "E-mail inválido");
        if (!List.of("PROFESSOR", "COORDENADOR", "ALUNO").contains(papel)) {
            throw erro(HttpStatus.BAD_REQUEST, "Papel inválido: use PROFESSOR, COORDENADOR ou ALUNO");
        }
        List<Disciplina> ds = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(disciplinaIds == null ? List.<UUID>of() : disciplinaIds)) {
            Disciplina d = disciplinaRepository.findById(id)
                    .orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Disciplina não encontrada"));
            if (d.getInstituicao() == null || !d.getInstituicao().getId().equals(inst.getId())) {
                throw erro(HttpStatus.FORBIDDEN, "A disciplina \"" + d.getNome() + "\" é de outra instituição");
            }
            d.exigirAtiva();
            ds.add(d);
        }
        return ds;
    }

    /** Cria o convite (cancelando pendentes anteriores do mesmo e-mail) e tenta enviar o e-mail. */
    @Transactional
    public Emitido emitir(Instituicao inst, String email, String nome, String papel, List<UUID> disciplinaIds,
                          boolean contaPreCriada) {
        String em = email.trim().toLowerCase();
        List<Disciplina> ds = validar(inst, em, papel, disciplinaIds);
        if (!contaPreCriada && accountRepository.findByEmail(em).isPresent()) {
            throw erro(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail");
        }
        for (Convite antigo : repository.findByInstituicaoIdAndEmailIgnoreCaseAndStatus(inst.getId(), em, Convite.PENDENTE)) {
            antigo.setStatus(Convite.CANCELADO);
            repository.save(antigo);
        }
        String token = novoToken();
        Convite c = new Convite();
        c.setInstituicao(inst);
        c.setEmail(em);
        c.setNome(nome == null || nome.isBlank() ? null : nome.trim());
        c.setPapel(papel);
        c.setDisciplinaIds(ds.stream().map(d -> d.getId().toString()).collect(Collectors.joining(",")));
        c.setTokenHash(hash(token));
        c.setContaPreCriada(contaPreCriada);
        c.setExpiraEm(Instant.now().plus(VALIDADE_DIAS, ChronoUnit.DAYS));
        c = repository.save(c);
        enviarEmail(c, token);
        return new Emitido(c, token);
    }

    private void enviarEmail(Convite c, String token) {
        String quem = c.getNome() == null ? "" : " " + c.getNome();
        emailService.enviarEmail(c.getEmail(), "Convite para " + c.getInstituicao().getNome(),
                "Olá" + quem + "!\n\n" + c.getInstituicao().getNome() + " convidou você para o Banco de Questões ("
                        + c.getPapel().toLowerCase() + ").\n\nCrie sua senha e entre pelo link (vale " + VALIDADE_DIAS
                        + " dias):\n" + link(token) + "\n\nSe você não esperava este convite, ignore este e-mail.");
    }

    @Transactional
    public ConviteDTO criar(UUID instituicaoId, CriarRequest req) {
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Instituição não encontrada"));
        Emitido e = emitir(inst, req.email() == null ? "" : req.email(), req.nome(), req.papel(), req.disciplinaIds(), false);
        auditoria.registrar(instituicaoId, "CONVITE_CRIADO", "Convite", e.convite().getId(),
                "Convite para " + e.convite().getEmail() + " como " + e.convite().getPapel(), null, null);
        return dto(e.convite(), e.token());
    }

    @Transactional(readOnly = true)
    public List<ConviteDTO> listar(UUID instituicaoId) {
        return repository.findByInstituicaoIdOrderByCriadoEmDesc(instituicaoId).stream()
                .map(c -> dto(c, null)).collect(Collectors.toList());
    }

    private Convite doGestor(UUID instituicaoId, UUID id) {
        Convite c = repository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Convite não encontrado"));
        if (!c.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Convite de outra instituição");
        return c;
    }

    @Transactional
    public ConviteDTO reenviar(UUID instituicaoId, UUID id) {
        Convite c = doGestor(instituicaoId, id);
        if (Convite.ACEITO.equals(c.getStatus())) throw erro(HttpStatus.CONFLICT, "Este convite já foi aceito");
        String token = novoToken(); // o link antigo deixa de valer
        c.setTokenHash(hash(token));
        c.setStatus(Convite.PENDENTE);
        c.setCriadoEm(Instant.now());
        c.setExpiraEm(Instant.now().plus(VALIDADE_DIAS, ChronoUnit.DAYS));
        repository.save(c);
        enviarEmail(c, token);
        auditoria.registrar(instituicaoId, "CONVITE_REENVIADO", "Convite", c.getId(), "Convite reenviado para " + c.getEmail(), null, null);
        return dto(c, token);
    }

    @Transactional
    public ConviteDTO cancelar(UUID instituicaoId, UUID id) {
        Convite c = doGestor(instituicaoId, id);
        if (Convite.ACEITO.equals(c.getStatus())) throw erro(HttpStatus.CONFLICT, "Este convite já foi aceito");
        c.setStatus(Convite.CANCELADO);
        repository.save(c);
        auditoria.registrar(instituicaoId, "CONVITE_CANCELADO", "Convite", c.getId(), "Convite cancelado: " + c.getEmail(), null, null);
        return dto(c, null);
    }

    // ------------------------------------------------------------------ lado do convidado (público)

    private Convite porToken(String token) {
        return repository.findByTokenHash(hash(token == null ? "" : token))
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Convite não encontrado"));
    }

    private void exigirUtilizavel(Convite c) {
        if (Convite.CANCELADO.equals(c.getStatus())) throw erro(HttpStatus.GONE, "Este convite foi cancelado");
        if (Convite.ACEITO.equals(c.getStatus())) throw erro(HttpStatus.GONE, "Este convite já foi aceito");
        if (c.expirado()) throw erro(HttpStatus.GONE, "Este convite expirou. Peça um novo à instituição");
    }

    @Transactional(readOnly = true)
    public Info info(String token) {
        Convite c = porToken(token);
        return new Info(c.getInstituicao().getNome(), c.getEmail(), c.getNome(), c.getPapel(),
                disciplinas(c).stream().map(DisciplinaRef::nome).collect(Collectors.toList()), c.statusEfetivo(),
                c.isContaPreCriada());
    }

    @Transactional
    public Map<String, String> aceitar(String token, AceiteRequest req) {
        Convite c = porToken(token);
        exigirUtilizavel(c);
        if (!senhaForte(req.senha())) {
            throw erro(HttpStatus.BAD_REQUEST, "A senha precisa ter 8 ou mais caracteres, com maiúscula, minúscula e número");
        }
        Instituicao inst = c.getInstituicao();
        String hashSenha = passwordEncoder.encode(req.senha());
        Optional<Account> existente = accountRepository.findByEmail(c.getEmail());
        Professor professor = null;
        Estudante estudante = null;

        if (c.isContaPreCriada()) {
            Account acc = existente.orElseThrow(() -> erro(HttpStatus.CONFLICT, "Conta não encontrada para este convite"));
            acc.setSenha(hashSenha);
            accountRepository.save(acc);
            professor = acc.getProfessorProfile();
            estudante = acc.getEstudanteProfile();
            if (professor != null) { professor.setSenha(hashSenha); professorRepository.save(professor); }
            if (estudante != null) { estudante.setSenha(hashSenha); estudanteRepository.save(estudante); }
        } else {
            if (existente.isPresent()) throw erro(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail");
            String nome = req.nome() != null && !req.nome().isBlank() ? req.nome().trim() : c.getNome();
            if (nome == null || nome.isBlank()) throw erro(HttpStatus.BAD_REQUEST, "Informe seu nome");
            if ("ALUNO".equals(c.getPapel())) {
                if (req.dataNascimento() == null || req.dataNascimento().isAfter(LocalDate.now())) {
                    throw erro(HttpStatus.BAD_REQUEST, "Informe uma data de nascimento válida");
                }
                estudante = criarEstudante(inst, nome, c.getEmail(), hashSenha, req.dataNascimento());
            } else {
                professor = criarProfessor(inst, nome, c.getEmail(), hashSenha, "COORDENADOR".equals(c.getPapel()));
            }
        }

        vincular(c, professor, estudante);
        if (professor != null && "COORDENADOR".equals(c.getPapel()) && !"COORDENADOR".equals(professor.getPapelInstituicao())) {
            professor.setPapelInstituicao("COORDENADOR");
            professorRepository.save(professor);
        }
        c.setStatus(Convite.ACEITO);
        c.setAceitoEm(Instant.now());
        repository.save(c);
        notificacaoService.criar(inst.getId(), TipoNotificacao.GERAL, "Convite aceito",
                c.getEmail() + " aceitou o convite e já pode entrar.", "/instituicao/convites");
        auditoria.registrar(inst.getId(), "CONVITE_ACEITO", "Convite", c.getId(), c.getEmail() + " aceitou o convite como " + c.getPapel(), null, null);
        return Map.of("email", c.getEmail(), "papel", c.getPapel());
    }

    private void vincular(Convite c, Professor professor, Estudante estudante) {
        for (DisciplinaRef ref : disciplinas(c)) {
            Disciplina d = disciplinaRepository.findById(ref.id()).orElse(null);
            if (d == null || d.isArquivada()) continue;
            if (professor != null) d.setProfessor(professor);
            if (estudante != null) vincularAluno(d, estudante);
            disciplinaRepository.save(d);
        }
    }

    // ------------------------------------------------------------------ criação de usuários (também usada na importação)

    @Transactional
    public Professor criarProfessor(Instituicao inst, String nome, String email, String senhaHash, boolean coordenador) {
        Professor p = new Professor();
        p.setNome(nome);
        p.setEmail(email);
        p.setSenha(senhaHash);
        p.setInstituicao(inst);
        if (coordenador) p.setPapelInstituicao("COORDENADOR");
        p = professorRepository.save(p);
        Account a = new Account();
        a.setEmail(email);
        a.setSenha(senhaHash);
        a.setRole(Role.PROFESSOR);
        a.setProfessorProfile(p);
        accountRepository.save(a);
        return p;
    }

    @Transactional
    public Estudante criarEstudante(Instituicao inst, String nome, String email, String senhaHash, LocalDate nascimento) {
        Estudante e = new Estudante();
        e.setNome(nome);
        e.setEmail(email);
        e.setSenha(senhaHash);
        e.setInstituicao(inst.getNome());
        e.setDataNascimento(Date.from(nascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()));
        e = estudanteRepository.save(e);
        Account a = new Account();
        a.setEmail(email);
        a.setSenha(senhaHash);
        a.setRole(Role.ALUNO);
        a.setEstudanteProfile(e);
        accountRepository.save(a);
        return e;
    }

    /** Matricula o aluno na disciplina (os dois lados da relação). */
    @Transactional
    public void vincularAluno(Disciplina d, Estudante e) {
        if (d.getEstudantes().stream().noneMatch(x -> x.getId().equals(e.getId()))) d.getEstudantes().add(e);
        if (e.getDisciplina().stream().noneMatch(x -> x.getId().equals(d.getId()))) {
            e.getDisciplina().add(d);
            estudanteRepository.save(e);
        }
        disciplinaRepository.save(d);
    }
}
