package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Popula o banco com dados de exemplo (mock) para testes.
 * Só existe quando seed.enabled=true (application.properties). Desligue em produção.
 */
@RestController
@RequestMapping("/seed")
@ConditionalOnProperty(name = "seed.enabled", havingValue = "true")
public class SeedController {

    private static final String SENHA = "123456";
    private static final String EMAIL_INSTITUICAO = "escola@mock.com";
    private static final String EMAIL_PROFESSOR = "professor@mock.com";
    private static final String EMAIL_ALUNO1 = "aluno1@mock.com";
    private static final String EMAIL_ALUNO2 = "aluno2@mock.com";

    private final InstituicaoRepository instituicaoRepository;
    private final ProfessorRepositores professorRepository;
    private final EstudanteRepositores estudanteRepository;
    private final DisciplinaRepository disciplinaRepository;
    private final ListaRepository listaRepository;
    private final QuestaoRepositores questaoRepository;
    private final RespostaEstudantesRepository respostaRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public SeedController(InstituicaoRepository instituicaoRepository,
                          ProfessorRepositores professorRepository,
                          EstudanteRepositores estudanteRepository,
                          DisciplinaRepository disciplinaRepository,
                          ListaRepository listaRepository,
                          QuestaoRepositores questaoRepository,
                          RespostaEstudantesRepository respostaRepository,
                          AccountRepository accountRepository,
                          PasswordEncoder passwordEncoder) {
        this.instituicaoRepository = instituicaoRepository;
        this.professorRepository = professorRepository;
        this.estudanteRepository = estudanteRepository;
        this.disciplinaRepository = disciplinaRepository;
        this.listaRepository = listaRepository;
        this.questaoRepository = questaoRepository;
        this.respostaRepository = respostaRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** POST /seed/popular — idempotente: se já foi executado, devolve os dados existentes. */
    @PostMapping("/popular")
    @Transactional
    public ResponseEntity<Map<String, Object>> popular() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("senhaDeTodos", SENHA);

        Optional<Instituicao> existente = instituicaoRepository.findByEmail(EMAIL_INSTITUICAO);
        if (existente.isPresent()) {
            Professor prof = professorRepository.findByEmail(EMAIL_PROFESSOR).orElse(null);
            out.put("mensagem", "Seed já executado anteriormente.");
            out.put("instituicaoId", existente.get().getId());
            if (prof != null) {
                out.put("professorId", prof.getId());
                out.put("listaIds", listaRepository.findByProfessorId(prof.getId()).stream().map(Lista::getId).toList());
            }
            out.put("logins", List.of(EMAIL_INSTITUICAO, EMAIL_PROFESSOR, EMAIL_ALUNO1, EMAIL_ALUNO2));
            return ResponseEntity.ok(out);
        }

        String hash = passwordEncoder.encode(SENHA);

        // Instituição
        Instituicao inst = new Instituicao();
        inst.setNome("Escola Mock");
        inst.setEmail(EMAIL_INSTITUICAO);
        inst.setSenha(hash);
        inst.setEndereco("Rua das Flores, 100");
        inst.setDataDeFundacao(new Date());
        inst = instituicaoRepository.save(inst);
        criarConta(EMAIL_INSTITUICAO, hash, Role.INSTITUICAO, inst, null, null);

        // Professor
        Professor prof = new Professor();
        prof.setNome("Prof. Mock");
        prof.setEmail(EMAIL_PROFESSOR);
        prof.setSenha(hash);
        prof.setInstituicao(inst);
        prof.setDataNascimento(new Date());
        prof = professorRepository.save(prof);
        criarConta(EMAIL_PROFESSOR, hash, Role.PROFESSOR, null, prof, null);

        // Estudantes
        Estudante a1 = novoEstudante("Aluno Um", EMAIL_ALUNO1, hash, inst);
        Estudante a2 = novoEstudante("Aluno Dois", EMAIL_ALUNO2, hash, inst);
        criarConta(EMAIL_ALUNO1, hash, Role.ALUNO, null, null, a1);
        criarConta(EMAIL_ALUNO2, hash, Role.ALUNO, null, null, a2);

        // Disciplina
        Disciplina disc = new Disciplina();
        disc.setNome("Língua Portuguesa");
        disc.setInstituicao(inst);
        disc.setProfessor(prof);
        disc.setEstudantes(new ArrayList<>(List.of(a1, a2)));
        disc = disciplinaRepository.save(disc);

        prof.setMateria1(disc.getId());
        professorRepository.save(prof);
        for (Estudante e : List.of(a1, a2)) {
            e.getDisciplina().add(disc);
            estudanteRepository.save(e);
        }

        // Lista
        Lista lista = new Lista();
        lista.setTitulo("Lista 1 - Interpretação de texto");
        lista.setProfessor(prof);
        lista.setDisciplina(disc);
        lista.setEstudantes(new ArrayList<>(List.of(a1, a2)));
        lista.setQuestoes(new ArrayList<>());
        lista = listaRepository.save(lista);

        // Questões (a última sem gabarito, para testar null)
        List<Questao> questoes = new ArrayList<>();
        questoes.add(novaQuestao(lista, "Enem/2013", "Qual é a capital do Brasil?",
                List.of("A) São Paulo", "B) Brasília", "C) Rio de Janeiro", "D) Salvador", "E) Recife"), 1));
        questoes.add(novaQuestao(lista, "FGV/2020", "Assinale a alternativa em que há uma oração adversativa.",
                List.of("A) Estudou muito e passou.", "B) Estudou muito, mas não passou.", "C) Ou estuda ou trabalha.",
                        "D) Estudou, logo passou.", "E) Passou porque estudou."), 1));
        questoes.add(novaQuestao(lista, "Cebraspe/2021", "Em \"Os alunos chegaram cedo\", o sujeito é:",
                List.of("A) Simples", "B) Composto", "C) Oculto", "D) Indeterminado", "E) Inexistente"), 0));
        questoes.add(novaQuestao(lista, "Vunesp/2019", "Assinale a palavra escrita corretamente.",
                List.of("A) Exceção", "B) Excessão", "C) Esceção", "D) Exceçao", "E) Eceção"), 0));
        questoes.add(novaQuestao(lista, "FCC/2018", "Qual figura de linguagem aparece em \"Seus olhos são duas estrelas\"?",
                List.of("A) Metonímia", "B) Ironia", "C) Metáfora", "D) Hipérbole", "E) Eufemismo"), null));

        // Respostas: aluno 1 acerta quase tudo, aluno 2 erra algumas
        responder(questoes, a1, new int[]{1, 1, 0, 0, 2});
        responder(questoes, a2, new int[]{1, 0, 2, 1, 2});

        out.put("mensagem", "Banco populado com sucesso.");
        out.put("instituicaoId", inst.getId());
        out.put("professorId", prof.getId());
        out.put("disciplinaId", disc.getId());
        out.put("listaId", lista.getId());
        out.put("estudanteIds", List.of(a1.getId(), a2.getId()));
        out.put("questaoIds", questoes.stream().map(Questao::getId).toList());
        out.put("logins", List.of(EMAIL_INSTITUICAO, EMAIL_PROFESSOR, EMAIL_ALUNO1, EMAIL_ALUNO2));
        return ResponseEntity.ok(out);
    }

    private Estudante novoEstudante(String nome, String email, String hash, Instituicao inst) {
        Estudante e = new Estudante();
        e.setNome(nome);
        e.setEmail(email);
        e.setSenha(hash);
        e.setDataNascimento(new Date());
        e.setInstituicao(inst.getNome());
        return estudanteRepository.save(e);
    }

    private void criarConta(String email, String hash, Role role, Instituicao inst, Professor prof, Estudante est) {
        Account acc = new Account();
        acc.setEmail(email);
        acc.setSenha(hash);
        acc.setRole(role);
        acc.setInstituicaoProfile(inst);
        acc.setProfessorProfile(prof);
        acc.setEstudanteProfile(est);
        accountRepository.save(acc);
    }

    private Questao novaQuestao(Lista lista, String fonte, String enunciado, List<String> alternativas, Integer gabarito) {
        Questao q = new Questao();
        q.setCabecalho("(" + fonte + ")");
        q.setEnunciado(enunciado);
        q.setGabarito(gabarito);
        q.setLista(lista);
        q.setAlternativasTexto(alternativas);
        return questaoRepository.save(q);
    }

    private void responder(List<Questao> questoes, Estudante est, int[] escolhas) {
        for (int i = 0; i < questoes.size(); i++) {
            Questao q = questoes.get(i);
            boolean correta = q.getGabarito() != null && q.getGabarito() == escolhas[i];
            respostaRepository.save(new RespostaEstudantes(null, q, correta, escolhas[i], est));
        }
    }
}
