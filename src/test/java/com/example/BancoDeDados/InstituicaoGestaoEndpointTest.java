package com.example.BancoDeDados;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.Security.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FE-301 a FE-309 pela API (contrato em docs/instituicao-api.md do front). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class InstituicaoGestaoEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenService tokenService;
    @Autowired AccountRepository accountRepository;
    @Autowired EstudanteRepositores estudanteRepository;
    @Autowired ProfessorRepositores professorRepository;
    @Autowired DisciplinaRepository disciplinaRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired ListaRepository listaRepository;
    @PersistenceContext EntityManager em;

    JsonNode seed;
    UUID disciplinaId;
    UUID listaId;
    Estudante aluno1;
    Estudante aluno2;

    @BeforeEach
    void seed() throws Exception {
        seed = json.readTree(mvc.perform(post("/seed/popular")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        disciplinaId = UUID.fromString(seed.get("disciplinaId").asText());
        listaId = UUID.fromString(seed.get("listaId").asText());
        aluno1 = estudanteRepository.findByEmail("aluno1@mock.com").orElseThrow();
        aluno2 = estudanteRepository.findByEmail("aluno2@mock.com").orElseThrow();
        em.flush();
        em.clear();
    }

    private String token(String email) {
        return "Bearer " + tokenService.gerarToken(accountRepository.findByEmail(email).orElseThrow());
    }

    private String inst() { return token(seed.get("logins").get(0).asText()); }
    private String prof() { return token("professor@mock.com"); }

    private JsonNode ok(MockHttpServletRequestBuilder r) throws Exception {
        String c = mvc.perform(r).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return c.isBlank() ? json.createObjectNode() : json.readTree(c);
    }

    private MockHttpServletRequestBuilder jsonReq(MockHttpServletRequestBuilder b, String auth, Object body) throws Exception {
        return b.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
    }

    private MockMultipartFile csv(String conteudo) {
        return new MockMultipartFile("arquivo", "x.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private String tokenDoConvite(String link) {
        return link.substring(link.lastIndexOf('/') + 1);
    }

    // ------------------------------------------------------------------ permissões (FE-306)

    @Test
    void rotasDeGestaoExigemPermissaoDaMatriz() throws Exception {
        mvc.perform(get("/instituicao/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/instituicao/dashboard").header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/instituicao/dashboard").header("Authorization", prof())).andExpect(status().isForbidden());
        mvc.perform(get("/instituicao/auditoria").header("Authorization", prof())).andExpect(status().isForbidden());
        mvc.perform(get("/instituicao/permissoes").header("Authorization", prof())).andExpect(status().isForbidden());
        ok(get("/instituicao/dashboard").header("Authorization", inst()));
    }

    @Test
    void matrizEditavelPeloAdminEMenusDependemDela() throws Exception {
        JsonNode minhas = ok(get("/instituicao/minhas-permissoes").header("Authorization", prof()));
        assertEquals("PROFESSOR", minhas.get("papel").asText());
        assertTrue(minhas.get("permissoes").toString().contains("CORRIGIR"));
        assertFalse(minhas.get("permissoes").toString().contains("VER_AUDITORIA"));
        assertEquals("ADMIN", ok(get("/instituicao/minhas-permissoes").header("Authorization", inst())).get("papel").asText());

        // admin tira "corrigir" do professor: a rota de correção passa a negar
        ok(jsonReq(put("/instituicao/permissoes"), inst(), Map.of("papel", "PROFESSOR", "permissoes", List.of("BANCO_QUESTOES"))));
        mvc.perform(get("/correcao/pendentes").header("Authorization", prof())).andExpect(status().isForbidden());
        ok(get("/banco-questoes").header("Authorization", prof()));
        assertFalse(ok(get("/instituicao/minhas-permissoes").header("Authorization", prof())).get("permissoes").toString().contains("CORRIGIR"));

        // permissões reservadas ao admin e papéis fixos não são editáveis
        mvc.perform(jsonReq(put("/instituicao/permissoes"), inst(), Map.of("papel", "PROFESSOR", "permissoes", List.of("VER_AUDITORIA"))))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonReq(put("/instituicao/permissoes"), inst(), Map.of("papel", "ADMIN", "permissoes", List.of())))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonReq(put("/instituicao/permissoes"), inst(), Map.of("papel", "PROFESSOR", "permissoes", List.of("INVENTADA"))))
                .andExpect(status().isBadRequest());

        // coordenador (atribuído pela listagem) ganha o que a matriz dá ao papel
        UUID profId = UUID.fromString(seed.get("professorId").asText());
        JsonNode usuarios = ok(get("/instituicao/usuarios").header("Authorization", inst()));
        assertEquals("PROFESSOR", usuarios.get(0).get("papel").asText());
        ok(jsonReq(put("/instituicao/usuarios/" + profId + "/papel"), inst(), Map.of("papel", "COORDENADOR")));
        em.flush();
        em.clear();
        JsonNode coord = ok(get("/instituicao/minhas-permissoes").header("Authorization", prof()));
        assertEquals("COORDENADOR", coord.get("papel").asText());
        assertTrue(coord.get("permissoes").toString().contains("VER_DASHBOARD"));
        ok(get("/instituicao/dashboard").header("Authorization", prof()));
        mvc.perform(get("/instituicao/auditoria").header("Authorization", prof())).andExpect(status().isForbidden());

        // tudo isso ficou no log
        JsonNode log = ok(get("/instituicao/auditoria").header("Authorization", inst()));
        String acoes = log.get("acoes").toString();
        assertTrue(acoes.contains("PERMISSOES_ALTERADAS") && acoes.contains("PAPEL_ALTERADO"), acoes);
    }

    // ------------------------------------------------------------------ importação (FE-301)

    @Test
    void importacaoValidaLinhaALinhaPreviaConfirmaERelata() throws Exception {
        String arquivo = "nome;email;data_nascimento;disciplinas\n"
                + "Carla Nova;carla@escola.com;2005-03-10;Língua Portuguesa\n"          // novo
                + "Aluno Um;aluno1@mock.com;2000-01-01;Língua Portuguesa\n"              // existente
                + ";sem-nome@x.com;2005-01-01;\n"                                        // nome vazio
                + "Dado Ruim;nao-e-email;2005-01-01;\n"                                  // e-mail inválido
                + "Carla Repetida;CARLA@escola.com;2005-03-10;\n"                        // duplicado no arquivo
                + "Data Ruim;datar@x.com;31/02/2005;\n"                                  // data inválida
                + "Disc Ruim;disc@x.com;2005-01-01;Matéria Que Não Existe\n";            // disciplina inexistente
        JsonNode previa = json.readTree(mvc.perform(multipart("/instituicao/importacao/previa").file(csv(arquivo))
                        .param("tipo", "ALUNOS").header("Authorization", inst()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(7, previa.at("/resumo/total").asInt());
        assertEquals(1, previa.at("/resumo/novos").asInt());
        assertEquals(5, previa.at("/resumo/erros").asInt());
        JsonNode linhas = previa.get("linhas");
        assertEquals("NOVO", linhas.get(0).get("status").asText());
        assertEquals(2, linhas.get(0).get("numero").asInt());
        assertTrue(linhas.get(2).get("erros").toString().contains("Nome obrigatório"));
        assertTrue(linhas.get(3).get("erros").toString().contains("E-mail inválido"));
        assertTrue(linhas.get(4).get("erros").toString().contains("duplicado"));
        assertTrue(linhas.get(5).get("erros").toString().contains("Data de nascimento inválida"));
        assertTrue(linhas.get(6).get("erros").toString().contains("não encontrada"));
        assertTrue(estudanteRepository.findByEmail("carla@escola.com").isEmpty(), "a prévia não grava nada");

        JsonNode res = json.readTree(mvc.perform(multipart("/instituicao/importacao/confirmar").file(csv(arquivo))
                        .param("tipo", "ALUNOS").header("Authorization", inst()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, res.at("/resumo/novos").asInt());
        assertEquals(5, res.get("comErro").size());
        assertEquals(1, res.get("convitesGerados").asInt());
        Estudante carla = estudanteRepository.findByEmail("carla@escola.com").orElseThrow();
        assertEquals("Escola Mock", carla.getInstituicao());
        em.flush();
        em.clear();
        assertEquals(3, disciplinaRepository.findById(disciplinaId).orElseThrow().getEstudantes().size());

        // reenviar o mesmo arquivo: ninguém é criado de novo
        JsonNode segunda = json.readTree(mvc.perform(multipart("/instituicao/importacao/previa").file(csv(arquivo))
                .param("tipo", "ALUNOS").header("Authorization", inst())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(0, segunda.at("/resumo/novos").asInt());
        assertEquals(2, segunda.at("/resumo/ignorados").asInt());
    }

    @Test
    void importacaoRejeitaArquivoSemColunasObrigatoriasEPapelErrado() throws Exception {
        mvc.perform(multipart("/instituicao/importacao/previa").file(csv("nome;email\nA;a@x.com\n")).param("tipo", "ALUNOS")
                .header("Authorization", inst())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/instituicao/importacao/previa").file(csv("nome;email\n")).param("tipo", "PROFESSORES")
                .header("Authorization", inst())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/instituicao/importacao/previa").file(csv("nome;email\nA;a@x.com\n")).param("tipo", "PAIS")
                .header("Authorization", inst())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/instituicao/importacao/previa").file(csv("nome;email\nA;a@x.com\n")).param("tipo", "PROFESSORES")
                .header("Authorization", prof())).andExpect(status().isForbidden());
        // e-mail de aluno já existente não pode virar professor
        JsonNode p = json.readTree(mvc.perform(multipart("/instituicao/importacao/previa")
                .file(csv("nome,email\nAluno Um,aluno1@mock.com\nProf Novo,novo.prof@escola.com\n")).param("tipo", "PROFESSORES")
                .header("Authorization", inst())).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("ERRO", p.get("linhas").get(0).get("status").asText());
        assertEquals("NOVO", p.get("linhas").get(1).get("status").asText());
    }

    // ------------------------------------------------------------------ convites (FE-302)

    @Test
    void conviteCompletoDaCriacaoAoAceiteComVinculoEReenvioECancelamento() throws Exception {
        JsonNode c = json.readTree(mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of(
                        "email", "Nova.Prof@escola.com", "nome", "Nova Professora", "papel", "PROFESSOR", "disciplinaIds", List.of(disciplinaId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("PENDENTE", c.get("status").asText());
        assertEquals("nova.prof@escola.com", c.get("email").asText());
        assertEquals(1, c.get("disciplinas").size());
        String token = tokenDoConvite(c.get("link").asText());
        UUID id = UUID.fromString(c.get("id").asText());

        // lista não expõe o link
        JsonNode lista = ok(get("/instituicao/convites").header("Authorization", inst()));
        assertEquals(1, lista.size());
        assertTrue(lista.get(0).get("link").isNull());

        // convidado (sem login) vê o convite
        JsonNode info = ok(get("/convites/" + token));
        assertEquals("Escola Mock", info.get("instituicao").asText());
        assertEquals("PENDENTE", info.get("status").asText());
        mvc.perform(get("/convites/token-invalido")).andExpect(status().isNotFound());

        // senha fraca é recusada
        mvc.perform(jsonReq(post("/convites/" + token + "/aceitar"), "Bearer x", Map.of("senha", "fraca"))).andExpect(status().isBadRequest());
        // reenviar invalida o link antigo
        JsonNode re = ok(post("/instituicao/convites/" + id + "/reenviar").header("Authorization", inst()));
        String novoToken = tokenDoConvite(re.get("link").asText());
        assertNotEquals(token, novoToken);
        mvc.perform(get("/convites/" + token)).andExpect(status().isNotFound());

        JsonNode ac = ok(post("/convites/" + novoToken + "/aceitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"Senha1234\"}"));
        assertEquals("nova.prof@escola.com", ac.get("email").asText());
        em.flush();
        em.clear();

        // a nova professora já entra e é dona da disciplina
        JsonNode login = ok(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nova.prof@escola.com\",\"senha\":\"Senha1234\"}"));
        assertNotNull(login.get("token"));
        Professor nova = professorRepository.findByEmail("nova.prof@escola.com").orElseThrow();
        assertEquals(nova.getId(), disciplinaRepository.findById(disciplinaId).orElseThrow().getProfessor().getId());
        assertEquals("ACEITO", ok(get("/instituicao/convites").header("Authorization", inst())).get(0).get("status").asText());
        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n -> n.getTitulo().equals("Convite aceito")));

        // não aceita duas vezes, não cancela um aceito
        mvc.perform(post("/convites/" + novoToken + "/aceitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"Senha1234\"}")).andExpect(status().isGone());
        mvc.perform(delete("/instituicao/convites/" + id).header("Authorization", inst())).andExpect(status().isConflict());
    }

    @Test
    void conviteCanceladoOuExpiradoNaoPodeSerAceitoEAlunoPrecisaDeNascimento() throws Exception {
        JsonNode c = json.readTree(mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of(
                "email", "aluno.novo@escola.com", "papel", "ALUNO", "disciplinaIds", List.of(disciplinaId))))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String token = tokenDoConvite(c.get("link").asText());
        // aluno sem nome/nascimento
        mvc.perform(post("/convites/" + token + "/aceitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"Senha1234\",\"nome\":\"Aluno Novo\"}")).andExpect(status().isBadRequest());
        ok(post("/convites/" + token + "/aceitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"Senha1234\",\"nome\":\"Aluno Novo\",\"dataNascimento\":\"2006-05-01\"}"));
        em.flush();
        em.clear();
        assertEquals(3, disciplinaRepository.findById(disciplinaId).orElseThrow().getEstudantes().size());

        JsonNode c2 = json.readTree(mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of(
                "email", "cancelado@escola.com", "papel", "PROFESSOR"))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        ok(delete("/instituicao/convites/" + c2.get("id").asText()).header("Authorization", inst()));
        mvc.perform(post("/convites/" + tokenDoConvite(c2.get("link").asText()) + "/aceitar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"senha\":\"Senha1234\",\"nome\":\"X\"}")).andExpect(status().isGone());
        assertEquals("CANCELADO", ok(get("/instituicao/convites").header("Authorization", inst())).get(0).get("status").asText());

        // e-mail que já tem conta não é convidado; papel inválido também
        mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of("email", "aluno1@mock.com", "papel", "PROFESSOR")))
                .andExpect(status().isConflict());
        mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of("email", "x@y.com", "papel", "REI")))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonReq(post("/instituicao/convites"), inst(), Map.of("email", "invalido", "papel", "PROFESSOR")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ dashboard e comparativos (FE-303 / FE-305)

    @Test
    void dashboardTemKpisVariacaoEFiltros() throws Exception {
        // dá um acesso a cada aluno hoje e uma entrega (finalizada agora) para a lista seed
        ok(get("/listas/estudante/" + aluno1.getId()).header("Authorization", token("aluno1@mock.com")));
        ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/finalizar").header("Authorization", token("aluno1@mock.com")));

        JsonNode d = ok(get("/instituicao/dashboard").header("Authorization", inst()));
        assertEquals(3, d.get("kpis").size());
        assertEquals("ALUNOS_ATIVOS", d.at("/kpis/0/chave").asText());
        assertEquals(1.0, d.at("/kpis/0/valor").asDouble(), 0.001);
        assertEquals(0.0, d.at("/kpis/0/anterior").asDouble(), 0.001);
        assertTrue(d.at("/kpis/0/variacaoPct").isNull(), "variação sobre zero não existe");
        assertTrue(d.at("/kpis/1/valor").asDouble() > 0);
        assertEquals(100.0, d.at("/kpis/2/valor").asDouble(), 0.001);
        assertEquals(1, d.get("disciplinas").size());

        // período do passado: sem entregas
        JsonNode antigo = ok(get("/instituicao/dashboard").param("inicio", "2020-01-01").param("fim", "2020-01-31").header("Authorization", inst()));
        assertTrue(antigo.at("/kpis/1/valor").isNull());
        // disciplina de outra instituição / inexistente
        mvc.perform(get("/instituicao/dashboard").param("disciplinaId", UUID.randomUUID().toString()).header("Authorization", inst()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/instituicao/dashboard").param("inicio", "2026-02-01").param("fim", "2026-01-01").header("Authorization", inst()))
                .andExpect(status().isBadRequest());

        JsonNode det = ok(get("/instituicao/dashboard/detalhe").param("kpi", "MEDIA_GERAL").header("Authorization", inst()));
        assertEquals(1, det.get("linhas").size());
        assertEquals("Língua Portuguesa", det.at("/linhas/0/nome").asText());
        mvc.perform(get("/instituicao/dashboard/detalhe").param("kpi", "OUTRO").header("Authorization", inst())).andExpect(status().isBadRequest());
    }

    @Test
    void comparativoPorDisciplinaTurmaEProfessorComExportacao() throws Exception {
        JsonNode porDisc = ok(get("/instituicao/relatorios/comparativo").param("por", "DISCIPLINA").header("Authorization", inst()));
        assertEquals(1, porDisc.get("linhas").size());
        assertEquals(1, ok(get("/instituicao/relatorios/comparativo").param("por", "PROFESSOR").header("Authorization", inst())).get("linhas").size());
        mvc.perform(get("/instituicao/relatorios/comparativo").param("por", "COR").header("Authorization", inst())).andExpect(status().isBadRequest());

        byte[] csv = mvc.perform(get("/instituicao/relatorios/comparativo/exportar").param("por", "DISCIPLINA").param("formato", "csv")
                .header("Authorization", inst())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertTrue(new String(csv, StandardCharsets.UTF_8).contains("Língua Portuguesa"));
        byte[] pdf = mvc.perform(get("/instituicao/relatorios/comparativo/exportar").param("por", "DISCIPLINA").param("formato", "pdf")
                .header("Authorization", inst())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(pdf)) { assertEquals(1, doc.getNumberOfPages()); }
        mvc.perform(get("/instituicao/relatorios/comparativo/exportar").param("por", "DISCIPLINA").param("formato", "xls")
                .header("Authorization", inst())).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ períodos, turmas, arquivamento, boletim (FE-304 / FE-308)

    private UUID criarPeriodo(String nome, String inicio, String fim) throws Exception {
        return UUID.fromString(ok(jsonReq(post("/instituicao/periodos"), inst(), Map.of("nome", nome, "inicio", inicio, "fim", fim))).get("id").asText());
    }

    @Test
    void periodosTurmasRematriculaEArquivamento() throws Exception {
        mvc.perform(jsonReq(post("/instituicao/periodos"), inst(), Map.of("nome", "2026/1", "inicio", "2026-06-30", "fim", "2026-02-01")))
                .andExpect(status().isBadRequest());
        UUID p1 = criarPeriodo("2026/1", "2026-02-01", "2026-06-30");
        UUID p2 = criarPeriodo("2026/2", "2026-08-01", "2026-12-15");
        ok(jsonReq(put("/instituicao/periodos/" + p1), inst(), Map.of("nome", "2026/1 (editado)", "inicio", "2026-02-02", "fim", "2026-06-30")));
        assertEquals(2, ok(get("/instituicao/periodos").header("Authorization", inst())).size());

        JsonNode t = ok(jsonReq(post("/instituicao/turmas"), inst(), Map.of("nome", "3º A", "periodoId", p1,
                "alunoIds", List.of(aluno1.getId(), aluno2.getId()), "disciplinaIds", List.of(disciplinaId))));
        UUID turmaId = UUID.fromString(t.get("id").asText());
        assertEquals(2, t.get("alunos").size());

        // rematrícula: nova turma no período seguinte, sem um aluno
        JsonNode r = ok(jsonReq(post("/instituicao/turmas/rematricula"), inst(), Map.of("turmaIds", List.of(turmaId),
                "periodoDestinoId", p2, "sufixoNome", "(2026/2)", "excluirAlunoIds", List.of(aluno2.getId()))));
        assertEquals(1, r.get("criadas").size());
        assertEquals(1, r.at("/criadas/0/alunos").size());
        assertEquals("3º A (2026/2)", r.at("/criadas/0/nome").asText());
        // repetir é ignorado, e mesmo período é recusado
        assertEquals(1, ok(jsonReq(post("/instituicao/turmas/rematricula"), inst(), Map.of("turmaIds", List.of(turmaId),
                "periodoDestinoId", p2, "sufixoNome", "(2026/2)"))).get("ignoradas").size());
        mvc.perform(jsonReq(post("/instituicao/turmas/rematricula"), inst(), Map.of("turmaIds", List.of(turmaId), "periodoDestinoId", p1)))
                .andExpect(status().isBadRequest());
        assertEquals(1, ok(get("/instituicao/turmas").param("periodoId", p2.toString()).header("Authorization", inst())).size());

        // arquivar: histórico continua legível, mas respostas e alterações são bloqueadas
        assertTrue(ok(jsonReq(patch("/instituicao/disciplinas/" + disciplinaId + "/arquivar"), inst(), Map.of("arquivada", true))).get("arquivada").asBoolean());
        int qid = ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof())).get(0).get("id").asInt();
        ok(get("/listas/" + listaId + "/questoes").header("Authorization", token("aluno1@mock.com")));
        mvc.perform(jsonReq(post("/enviaresposta"), token("aluno1@mock.com"), Map.of("estudanteId", aluno1.getId(), "questaoId", qid,
                "alternativa", 0, "listaId", listaId))).andExpect(status().isBadRequest());
        mvc.perform(jsonReq(put("/listas/" + listaId + "/configuracao"), prof(), Map.of("maxTentativas", 2))).andExpect(status().isBadRequest());
        mvc.perform(multipart("/disciplinas/" + disciplinaId + "/avisos").header("Authorization", prof()).param("titulo", "t").param("texto", "x"))
                .andExpect(status().isBadRequest());
        // não dá para colocar arquivada numa turma nova
        mvc.perform(jsonReq(post("/instituicao/turmas"), inst(), Map.of("nome", "X", "periodoId", p2, "disciplinaIds", List.of(disciplinaId))))
                .andExpect(status().isBadRequest());
        assertTrue(ok(get("/instituicao/disciplinas-gestao").header("Authorization", inst())).get(0).get("arquivada").asBoolean());
        // desarquivar volta ao normal
        ok(jsonReq(patch("/instituicao/disciplinas/" + disciplinaId + "/arquivar"), inst(), Map.of("arquivada", false)));
        ok(jsonReq(put("/listas/" + listaId + "/configuracao"), prof(), Map.of("maxTentativas", 2)));
    }

    @Test
    void boletimDeTurmaEAlunoERelatorioDeRisco() throws Exception {
        UUID p1 = criarPeriodo("2026/1", "2026-01-01", "2026-12-31");
        UUID turmaId = UUID.fromString(ok(jsonReq(post("/instituicao/turmas"), inst(), Map.of("nome", "3º A", "periodoId", p1,
                "alunoIds", List.of(aluno1.getId(), aluno2.getId()), "disciplinaIds", List.of(disciplinaId)))).get("id").asText());

        byte[] lote = mvc.perform(get("/instituicao/boletim/turma/" + turmaId).header("Authorization", inst()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(lote)) { assertEquals(2, doc.getNumberOfPages(), "uma página por aluno"); }
        byte[] um = mvc.perform(get("/instituicao/boletim/aluno/" + aluno1.getId()).param("periodoId", p1.toString()).header("Authorization", inst()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(um)) { assertEquals(1, doc.getNumberOfPages()); }
        mvc.perform(get("/instituicao/boletim/turma/" + UUID.randomUUID()).header("Authorization", inst())).andExpect(status().isNotFound());
        mvc.perform(get("/instituicao/boletim/turma/" + turmaId).header("Authorization", prof())).andExpect(status().isForbidden());

        byte[] csv = mvc.perform(get("/instituicao/relatorios/risco-evasao").param("turmaId", turmaId.toString()).param("formato", "csv")
                .header("Authorization", inst())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texto = new String(csv, StandardCharsets.UTF_8);
        assertTrue(texto.contains("Aluno Um") && texto.contains("Aluno Dois") && texto.contains("Sem acesso"), texto);
        byte[] pdf = mvc.perform(get("/instituicao/relatorios/risco-evasao").param("turmaId", turmaId.toString())
                .header("Authorization", inst())).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(pdf)) { assertTrue(doc.getNumberOfPages() >= 1); }
    }

    // ------------------------------------------------------------------ configurações (FE-309)

    @Test
    void configuracoesValidamCorLogoEAplicamPadroesNasNovasListas() throws Exception {
        JsonNode inicial = ok(get("/instituicao/configuracoes").header("Authorization", inst()));
        assertFalse(inicial.get("temLogo").asBoolean());
        assertEquals(3, inicial.get("integracoes").size());
        assertEquals("EM_BREVE", inicial.at("/integracoes/0/situacao").asText());

        mvc.perform(jsonReq(put("/instituicao/configuracoes"), inst(), Map.of("corPrincipal", "vermelho"))).andExpect(status().isBadRequest());
        mvc.perform(jsonReq(put("/instituicao/configuracoes"), inst(), Map.of("corPrincipal", "#ffff99"))).andExpect(status().isBadRequest()); // contraste
        mvc.perform(jsonReq(put("/instituicao/configuracoes"), inst(), Map.of("tentativasPadrao", 99))).andExpect(status().isBadRequest());
        mvc.perform(jsonReq(put("/instituicao/configuracoes"), prof(), Map.of("corPrincipal", "#1d4ed8"))).andExpect(status().isForbidden());

        JsonNode salva = ok(jsonReq(put("/instituicao/configuracoes"), inst(), Map.of("corPrincipal", "#7C3AED",
                "tentativasPadrao", 3, "tempoLimitePadraoMinutos", 45, "gabaritoLiberadoPadrao", true)));
        assertEquals("#7c3aed", salva.get("corPrincipal").asText());

        // logo: só PNG/JPG de verdade
        mvc.perform(multipart("/instituicao/configuracoes/logo").file(new MockMultipartFile("logo", "x.png", "image/png", "nao-sou-imagem".getBytes()))
                .header("Authorization", inst())).andExpect(status().isBadRequest());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        JsonNode comLogo = json.readTree(mvc.perform(multipart("/instituicao/configuracoes/logo").file(new MockMultipartFile("logo", "l.png", "image/png", png))
                .header("Authorization", inst())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(comLogo.get("temLogo").asBoolean());
        UUID instId = UUID.fromString(seed.get("instituicaoId").asText());
        assertArrayEquals(png, mvc.perform(get("/branding/" + instId + "/logo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

        // branding chega a quem usa o app: aluno e professor da instituição
        assertEquals("#7c3aed", ok(get("/branding/minha").header("Authorization", token("aluno1@mock.com"))).get("corPrincipal").asText());
        assertEquals("#7c3aed", ok(get("/branding/minha").header("Authorization", prof())).get("corPrincipal").asText());
        mvc.perform(get("/branding/minha")).andExpect(status().isUnauthorized());

        // padrões aplicados a lista nova
        UUID profId = UUID.fromString(seed.get("professorId").asText());
        JsonNode nova = ok(post("/listas/disciplina/" + disciplinaId).param("titulo", "Nova com padrões")
                .param("professorId", profId.toString()).header("Authorization", prof()));
        JsonNode cfg = ok(get("/listas/" + nova.get("id").asText() + "/configuracao").header("Authorization", prof()));
        assertEquals(3, cfg.get("maxTentativas").asInt());
        assertEquals(45, cfg.get("tempoLimiteMinutos").asInt());
        em.flush();
        em.clear();
        assertTrue(listaRepository.findById(UUID.fromString(nova.get("id").asText())).orElseThrow().isGabaritoLiberado());

        assertFalse(ok(delete("/instituicao/configuracoes/logo").header("Authorization", inst())).get("temLogo").asBoolean());
        mvc.perform(get("/branding/" + instId + "/logo")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ auditoria (FE-307)

    @Test
    void auditoriaRegistraNotaAnteriorENovaEFiltra() throws Exception {
        // professor cria questão aberta, aluno responde e finaliza, professor corrige duas vezes (6,0 -> 7,5)
        ok(jsonReq(post("/listas/" + listaId + "/questoes"), prof(), Map.of("tipo", "ABERTA", "enunciado", "Explique.", "rubrica", "r")));
        em.flush();
        em.clear();
        int qid = -1;
        for (JsonNode q : ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof()))) {
            if ("ABERTA".equals(q.get("tipo").asText())) qid = q.get("id").asInt();
        }
        mvc.perform(jsonReq(post("/enviaresposta"), token("aluno1@mock.com"), Map.of("estudanteId", aluno1.getId(), "questaoId", qid,
                "listaId", listaId, "textoResposta", "Resposta"))).andExpect(status().isOk());
        ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/finalizar").header("Authorization", token("aluno1@mock.com")));
        long respostaId = ok(get("/correcao/pendentes").header("Authorization", prof())).at("/items/0/respostaId").asLong();
        ok(jsonReq(put("/correcao/respostas/" + respostaId), prof(), Map.of("nota", 6.0)));
        ok(jsonReq(put("/correcao/respostas/" + respostaId), prof(), Map.of("nota", 7.5, "feedback", "Revisei")));

        JsonNode log = ok(get("/instituicao/auditoria").param("acao", "NOTA_ALTERADA").header("Authorization", inst()));
        assertEquals(1, log.get("totalElements").asInt());
        JsonNode e = log.at("/items/0");
        assertEquals("6.00", e.get("valorAnterior").asText());
        assertEquals("7.50", e.get("valorNovo").asText());
        assertEquals("Prof. Mock", e.get("usuarioNome").asText());
        assertTrue(e.get("descricao").asText().contains("Aluno Um"));
        // a primeira correção foi "lançada" (sem nota anterior)
        JsonNode lancada = ok(get("/instituicao/auditoria").param("acao", "NOTA_LANCADA").header("Authorization", inst()));
        assertEquals("sem nota", lancada.at("/items/0/valorAnterior").asText());

        // filtros por usuário e por período
        UUID profId = UUID.fromString(seed.get("professorId").asText());
        assertTrue(ok(get("/instituicao/auditoria").param("usuarioId", profId.toString()).header("Authorization", inst())).get("totalElements").asInt() >= 2);
        assertEquals(0, ok(get("/instituicao/auditoria").param("usuarioId", UUID.randomUUID().toString()).header("Authorization", inst())).get("totalElements").asInt());
        assertEquals(0, ok(get("/instituicao/auditoria").param("inicio", "2020-01-01").param("fim", "2020-01-31").header("Authorization", inst())).get("totalElements").asInt());
        String hoje = LocalDate.now().toString();
        assertTrue(ok(get("/instituicao/auditoria").param("inicio", hoje).param("fim", hoje).header("Authorization", inst())).get("totalElements").asInt() >= 2);
        assertTrue(log.get("usuarios").size() >= 1);
        // só o admin vê
        mvc.perform(get("/instituicao/auditoria").header("Authorization", prof())).andExpect(status().isForbidden());
        mvc.perform(get("/instituicao/auditoria").header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        assertNotNull(Instant.now().plus(1, ChronoUnit.DAYS));
    }
}
