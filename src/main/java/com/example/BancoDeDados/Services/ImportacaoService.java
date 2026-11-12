package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/** FE-301: importação em massa de alunos e professores por CSV, com prévia e validação linha a linha. */
@Service
public class ImportacaoService {

    public static final int MAX_LINHAS = 2000;
    public static final long MAX_BYTES = 2L * 1024 * 1024;

    public record Linha(int numero, Map<String, String> dados, String status, List<String> erros) {}

    public record Resumo(int total, int novos, int atualizados, int ignorados, int erros) {}

    public record Previa(String tipo, List<String> colunas, List<Linha> linhas, Resumo resumo) {}

    public record Resultado(Resumo resumo, List<Linha> comErro, int convitesGerados) {}

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT);

    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private ProfessorRepositores professorRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private ConviteService conviteService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private AuditoriaService auditoria;

    private static ResponseStatusException erro(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim()
                .replaceAll("[\\s-]+", "_");
    }

    // ------------------------------------------------------------------ CSV

    /** Parser CSV com aspas (RFC 4180), BOM e separador ';' ou ','. */
    static List<List<String>> parseCsv(String texto) {
        String t = texto.startsWith("﻿") ? texto.substring(1) : texto;
        int fimPrimeira = t.indexOf('\n');
        String primeira = fimPrimeira < 0 ? t : t.substring(0, fimPrimeira);
        char sep = primeira.chars().filter(c -> c == ';').count() >= primeira.chars().filter(c -> c == ',').count() ? ';' : ',';
        List<List<String>> linhas = new ArrayList<>();
        List<String> atual = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (aspas) {
                if (c == '"') {
                    if (i + 1 < t.length() && t.charAt(i + 1) == '"') { campo.append('"'); i++; }
                    else aspas = false;
                } else campo.append(c);
            } else if (c == '"') {
                aspas = true;
            } else if (c == sep) {
                atual.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                atual.add(campo.toString());
                campo.setLength(0);
                if (atual.stream().anyMatch(s -> !s.isBlank())) linhas.add(atual);
                atual = new ArrayList<>();
            } else campo.append(c);
        }
        atual.add(campo.toString());
        if (atual.stream().anyMatch(s -> !s.isBlank())) linhas.add(atual);
        return linhas;
    }

    // ------------------------------------------------------------------ análise

    private static final class Item {
        int numero;
        Map<String, String> dados = new LinkedHashMap<>();
        List<String> erros = new ArrayList<>();
        String status;
        LocalDate nascimento;
        List<Disciplina> disciplinas = new ArrayList<>();
        Professor professor;
        Estudante estudante;
        String email;
    }

    private List<String> colunas(String tipo) {
        return "ALUNOS".equals(tipo) ? List.of("nome", "email", "data_nascimento", "disciplinas") : List.of("nome", "email", "disciplinas");
    }

    private List<Item> analisar(Instituicao inst, String tipo, byte[] bytes) {
        if (!"ALUNOS".equals(tipo) && !"PROFESSORES".equals(tipo)) throw erro("Tipo inválido: use ALUNOS ou PROFESSORES");
        if (bytes == null || bytes.length == 0) throw erro("O arquivo está vazio");
        if (bytes.length > MAX_BYTES) throw erro("O arquivo pode ter no máximo 2 MB");
        List<List<String>> linhas = parseCsv(new String(bytes, StandardCharsets.UTF_8));
        if (linhas.isEmpty()) throw erro("O arquivo está vazio");

        List<String> cab = linhas.get(0).stream().map(ImportacaoService::norm).collect(Collectors.toList());
        List<String> exigidas = "ALUNOS".equals(tipo) ? List.of("nome", "email", "data_nascimento") : List.of("nome", "email");
        for (String col : exigidas) {
            if (!cab.contains(col)) throw erro("Falta a coluna \"" + col + "\" no cabeçalho. Baixe o modelo de CSV.");
        }
        if (linhas.size() - 1 > MAX_LINHAS) throw erro("Máximo de " + MAX_LINHAS + " linhas por arquivo");
        if (linhas.size() == 1) throw erro("O arquivo só tem o cabeçalho, sem nenhuma linha de dados");

        Map<String, Disciplina> disciplinasPorNome = new HashMap<>();
        for (Disciplina d : disciplinaRepository.findByInstituicao_Id(inst.getId())) {
            disciplinasPorNome.putIfAbsent(norm(d.getNome()), d);
        }

        Set<String> vistos = new HashSet<>();
        List<Item> itens = new ArrayList<>();
        for (int i = 1; i < linhas.size(); i++) {
            List<String> cel = linhas.get(i);
            Item it = new Item();
            it.numero = i + 1; // linha no arquivo (1 = cabeçalho)
            for (String c : colunas(tipo)) {
                int idx = cab.indexOf(c);
                it.dados.put(c, idx >= 0 && idx < cel.size() ? cel.get(idx).trim() : "");
            }
            String nome = it.dados.get("nome");
            String email = it.dados.get("email").toLowerCase();
            it.email = email;
            if (nome.isEmpty()) it.erros.add("Nome obrigatório");
            else if (nome.length() > 150) it.erros.add("Nome com mais de 150 caracteres");
            if (email.isEmpty()) it.erros.add("E-mail obrigatório");
            else if (!ConviteService.EMAIL.matcher(email).matches() || email.length() > 150) it.erros.add("E-mail inválido");
            else if (!vistos.add(email)) it.erros.add("E-mail duplicado no arquivo (já aparece em linha anterior)");

            if ("ALUNOS".equals(tipo)) {
                String data = it.dados.get("data_nascimento");
                if (data.isEmpty()) it.erros.add("Data de nascimento obrigatória");
                else {
                    it.nascimento = parseData(data);
                    if (it.nascimento == null) it.erros.add("Data de nascimento inválida (use AAAA-MM-DD ou DD/MM/AAAA)");
                    else if (it.nascimento.isAfter(LocalDate.now())) it.erros.add("Data de nascimento no futuro");
                }
            }

            String ds = it.dados.get("disciplinas");
            if (!ds.isEmpty()) {
                for (String nomeD : ds.split("\\|")) {
                    if (nomeD.isBlank()) continue;
                    Disciplina d = disciplinasPorNome.get(norm(nomeD));
                    if (d == null) it.erros.add("Disciplina \"" + nomeD.trim() + "\" não encontrada");
                    else if (d.isArquivada()) it.erros.add("Disciplina \"" + d.getNome() + "\" está arquivada");
                    else if (!it.disciplinas.contains(d)) it.disciplinas.add(d);
                }
            }

            if (it.erros.isEmpty()) classificar(inst, tipo, it);
            else it.status = "ERRO";
            itens.add(it);
        }
        return itens;
    }

    private void classificar(Instituicao inst, String tipo, Item it) {
        Optional<Account> acc = accountRepository.findByEmail(it.email);
        if (acc.isEmpty()) {
            it.status = "NOVO";
            return;
        }
        Account a = acc.get();
        if ("ALUNOS".equals(tipo)) {
            Estudante e = a.getEstudanteProfile();
            if (e == null || !inst.getNome().equals(e.getInstituicao())) {
                it.erros.add("E-mail já cadastrado em outra instituição ou com outro papel");
                it.status = "ERRO";
                return;
            }
            it.estudante = e;
            boolean mudou = !e.getNome().equals(it.dados.get("nome"))
                    || (e.getDataNascimento() != null && !paraLocal(e.getDataNascimento()).equals(it.nascimento))
                    || it.disciplinas.stream().anyMatch(d -> e.getDisciplina().stream().noneMatch(x -> x.getId().equals(d.getId())));
            it.status = mudou ? "ATUALIZAR" : "IGNORAR";
        } else {
            Professor p = a.getProfessorProfile();
            if (p == null || p.getInstituicao() == null || !p.getInstituicao().getId().equals(inst.getId())) {
                it.erros.add("E-mail já cadastrado em outra instituição ou com outro papel");
                it.status = "ERRO";
                return;
            }
            it.professor = p;
            boolean mudou = !p.getNome().equals(it.dados.get("nome"))
                    || it.disciplinas.stream().anyMatch(d -> d.getProfessor() == null || !d.getProfessor().getId().equals(p.getId()));
            it.status = mudou ? "ATUALIZAR" : "IGNORAR";
        }
    }

    private static LocalDate parseData(String s) {
        for (DateTimeFormatter f : List.of(DateTimeFormatter.ISO_LOCAL_DATE, BR)) {
            try {
                return LocalDate.parse(s, f);
            } catch (DateTimeParseException ignored) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static LocalDate paraLocal(Date d) {
        return new java.sql.Date(d.getTime()).toLocalDate();
    }

    private Resumo resumo(List<Item> itens) {
        int n = 0, a = 0, ig = 0, er = 0;
        for (Item it : itens) {
            switch (it.status) {
                case "NOVO" -> n++;
                case "ATUALIZAR" -> a++;
                case "IGNORAR" -> ig++;
                default -> er++;
            }
        }
        return new Resumo(itens.size(), n, a, ig, er);
    }

    private static Linha linha(Item it) {
        return new Linha(it.numero, it.dados, it.status, it.erros);
    }

    // ------------------------------------------------------------------ API

    @Transactional(readOnly = true)
    public Previa previa(UUID instituicaoId, String tipo, byte[] bytes) {
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instituição não encontrada"));
        String t = tipo == null ? "" : tipo.toUpperCase();
        List<Item> itens = analisar(inst, t, bytes);
        return new Previa(t, colunas(t), itens.stream().map(ImportacaoService::linha).collect(Collectors.toList()), resumo(itens));
    }

    /** Reanalisa o arquivo e aplica as linhas válidas; as com erro são ignoradas e voltam no resultado. */
    @Transactional
    public Resultado confirmar(UUID instituicaoId, String tipo, byte[] bytes) {
        Instituicao inst = instituicaoRepository.findById(instituicaoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instituição não encontrada"));
        String t = tipo == null ? "" : tipo.toUpperCase();
        List<Item> itens = analisar(inst, t, bytes);
        int convites = 0;
        for (Item it : itens) {
            if ("ERRO".equals(it.status) || "IGNORAR".equals(it.status)) continue;
            if ("ALUNOS".equals(t)) {
                Estudante e = it.estudante;
                if ("NOVO".equals(it.status)) {
                    e = conviteService.criarEstudante(inst, it.dados.get("nome"), it.email,
                            passwordEncoder.encode(UUID.randomUUID().toString()), it.nascimento);
                    conviteService.emitir(inst, it.email, it.dados.get("nome"), "ALUNO", List.of(), true);
                    convites++;
                } else {
                    e.setNome(it.dados.get("nome"));
                    e.setDataNascimento(Date.from(it.nascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()));
                    estudanteRepository.save(e);
                }
                for (Disciplina d : it.disciplinas) conviteService.vincularAluno(d, e);
            } else {
                Professor p = it.professor;
                if ("NOVO".equals(it.status)) {
                    p = conviteService.criarProfessor(inst, it.dados.get("nome"), it.email,
                            passwordEncoder.encode(UUID.randomUUID().toString()), false);
                    conviteService.emitir(inst, it.email, it.dados.get("nome"), "PROFESSOR", List.of(), true);
                    convites++;
                } else {
                    p.setNome(it.dados.get("nome"));
                    professorRepository.save(p);
                }
                for (Disciplina d : it.disciplinas) {
                    d.setProfessor(p);
                    disciplinaRepository.save(d);
                }
            }
        }
        Resumo r = resumo(itens);
        auditoria.registrar(instituicaoId, "IMPORTACAO_" + t, "Importacao", null,
                "Importação de " + t.toLowerCase() + ": " + r.novos() + " criados, " + r.atualizados() + " atualizados, "
                        + r.ignorados() + " ignorados, " + r.erros() + " com erro", null, null);
        return new Resultado(r, itens.stream().filter(i -> "ERRO".equals(i.status)).map(ImportacaoService::linha).collect(Collectors.toList()),
                convites);
    }
}
