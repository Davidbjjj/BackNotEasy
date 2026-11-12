package com.example.BancoDeDados;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.Security.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cobre FE-201 a FE-209 do ponto de vista da API (contrato em docs/professor-api.md do front). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfessorFeaturesEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenService tokenService;
    @Autowired AccountRepository accountRepository;
    @Autowired EstudanteRepositores estudanteRepository;
    @Autowired ListaRepository listaRepository;
    @Autowired NotificacaoRepository notificacaoRepository;
    @Autowired ListaEstudanteNotaRepository notaRepository;
    @PersistenceContext EntityManager em;

    JsonNode seed;
    UUID listaId;
    UUID disciplinaId;
    Estudante aluno1;
    Estudante aluno2;

    @BeforeEach
    void seed() throws Exception {
        String body = mvc.perform(post("/seed/popular")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        seed = json.readTree(body);
        listaId = UUID.fromString(seed.get("listaId").asText());
        disciplinaId = UUID.fromString(seed.get("disciplinaId").asText());
        aluno1 = estudanteRepository.findByEmail("aluno1@mock.com").orElseThrow();
        aluno2 = estudanteRepository.findByEmail("aluno2@mock.com").orElseThrow();
        em.flush();
        em.clear(); // recarrega Lista.questoes do banco, como numa requisição real
    }

    private String token(String email) {
        return "Bearer " + tokenService.gerarToken(accountRepository.findByEmail(email).orElseThrow());
    }

    private String prof() { return token("professor@mock.com"); }

    /** Nota gravada do aluno na lista (lida do banco; o endpoint GET devolve a entidade inteira). */
    private double notaDe(Estudante aluno) {
        em.flush();
        em.clear();
        return notaRepository.findByListaIdAndEstudanteId(listaId, aluno.getId()).orElseThrow().getNota().doubleValue();
    }

    private JsonNode ok(MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        String c = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return c.isBlank() ? json.createObjectNode() : json.readTree(c);
    }

    private MockHttpServletRequestBuilder postJson(String url, String auth, Object body) throws Exception {
        return post(url).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
    }

    private int criarQuestaoAberta() throws Exception {
        ok(postJson("/listas/" + listaId + "/questoes", prof(), Map.of(
                "tipo", "ABERTA", "enunciado", "Explique a função da vírgula.", "rubrica", "Cita aposto e vocativo",
                "assunto", "Pontuação", "dificuldade", "MEDIA", "tags", List.of("gramática"))));
        em.flush();
        em.clear();
        JsonNode itens = ok(get("/banco-questoes").param("tipo", "ABERTA").header("Authorization", prof())).get("items");
        assertEquals(1, itens.size());
        return itens.get(0).get("id").asInt();
    }

    // ------------------------------------------------------------------ segurança

    @Test
    void rotasDoProfessorExigemAutenticacaoEPapel() throws Exception {
        mvc.perform(get("/correcao/pendentes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/correcao/pendentes").header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/banco-questoes").header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/professor/alunos-em-risco").header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        mvc.perform(post("/exportacoes").header("Authorization", token("aluno1@mock.com"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/listas/" + listaId + "/configuracao").header("Authorization", token("aluno1@mock.com"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ FE-201

    @Test
    void filaDeCorrecaoDaRespostaAoNotaELancaNotificacao() throws Exception {
        int questaoAberta = criarQuestaoAberta();

        // aluno 1 responde a aberta e finaliza (as 5 objetivas já vieram respondidas no seed)
        mvc.perform(postJson("/enviaresposta", token("aluno1@mock.com"), Map.of(
                "estudanteId", aluno1.getId(), "questaoId", questaoAberta, "listaId", listaId,
                "textoResposta", "A vírgula separa o aposto e isola o vocativo.")))
                .andExpect(status().isOk());
        // antes de finalizar não entra na fila
        assertEquals(0, ok(get("/correcao/pendentes").header("Authorization", prof())).get("total").asInt());

        JsonNode fin = ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/finalizar")
                .header("Authorization", token("aluno1@mock.com")));
        assertEquals(1, fin.get("pendenteCorrecao").asInt());

        JsonNode fila = ok(get("/correcao/pendentes").header("Authorization", prof()));
        assertEquals(1, fila.get("total").asInt());
        JsonNode item = fila.get("items").get(0);
        assertEquals("Aluno Um", item.get("estudanteNome").asText());
        assertEquals(1, fila.get("listas").size());
        assertEquals(0, ok(get("/correcao/pendentes").param("busca", "zzz").header("Authorization", prof())).get("total").asInt());
        assertEquals(1, ok(get("/correcao/pendentes").param("listaId", listaId.toString()).header("Authorization", prof())).get("total").asInt());

        long id = item.get("respostaId").asLong();
        JsonNode det = ok(get("/correcao/respostas/" + id).header("Authorization", prof()));
        assertEquals("Cita aposto e vocativo", det.get("rubrica").asText());
        assertEquals("A vírgula separa o aposto e isola o vocativo.", det.get("textoResposta").asText());

        // validações da nota
        mvc.perform(put("/correcao/respostas/" + id).header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nota\": 11}")).andExpect(status().isBadRequest());

        JsonNode res = ok(put("/correcao/respostas/" + id).header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nota\": 8, \"feedback\": \"Bom, faltou o vocativo.\"}"));
        assertTrue(res.get("notaLancada").asBoolean());
        assertEquals(0, res.get("restantes").asInt());

        // nota da lista: 4 objetivas certas + 0,8 da aberta = 4,8 de 6 questões = 8,0
        assertEquals(8.0, notaDe(aluno1), 0.001);

        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n ->
                n.getUsuarioId().equals(aluno1.getId()) && n.getTipo() == TipoNotificacao.NOTA_LANCADA));

        // fila vazia de novo; correção aparece na revisão do aluno
        assertEquals(0, ok(get("/correcao/pendentes").header("Authorization", prof())).get("total").asInt());
        ok(put("/listas/" + listaId + "/gabarito-liberado").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"liberado\": true}"));
        JsonNode rev = ok(get("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/revisao")
                .header("Authorization", token("aluno1@mock.com")));
        JsonNode aberta = null;
        for (JsonNode q : rev.get("questoes")) if ("ABERTA".equals(q.get("tipo").asText())) aberta = q;
        assertNotNull(aberta);
        assertEquals(8.0, aberta.get("notaQuestao").asDouble(), 0.001);
        assertEquals("Bom, faltou o vocativo.", aberta.get("feedback").asText());
        assertTrue(aberta.get("corrigida").asBoolean());
    }

    @Test
    void alunoNaoEnviaTextoVazioEmQuestaoAberta() throws Exception {
        int questaoAberta = criarQuestaoAberta();
        mvc.perform(postJson("/enviaresposta", token("aluno1@mock.com"), Map.of(
                "estudanteId", aluno1.getId(), "questaoId", questaoAberta, "listaId", listaId, "textoResposta", "  ")))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ FE-202

    @Test
    void bancoDeQuestoesBuscaFiltraClassificaEReaproveita() throws Exception {
        JsonNode todas = ok(get("/banco-questoes").header("Authorization", prof()));
        assertEquals(5, todas.get("totalElements").asInt());

        assertEquals(1, ok(get("/banco-questoes").param("q", "capital").header("Authorization", prof())).get("totalElements").asInt());
        assertEquals(1, ok(get("/banco-questoes").param("q", "CAPITAL do brasil").header("Authorization", prof())).get("totalElements").asInt());

        int id = ok(get("/banco-questoes").param("q", "capital").header("Authorization", prof())).get("items").get(0).get("id").asInt();
        JsonNode editada = ok(patch("/banco-questoes/" + id).header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tags\": [\"Geografia\", \" geografia \", \"enem\"], \"dificuldade\": \"FACIL\", \"assunto\": \"Capitais\"}"));
        assertEquals(2, editada.get("tags").size());
        assertEquals("FACIL", editada.get("dificuldade").asText());

        assertEquals(1, ok(get("/banco-questoes").param("tag", "enem").header("Authorization", prof())).get("totalElements").asInt());
        assertEquals(1, ok(get("/banco-questoes").param("dificuldade", "FACIL").header("Authorization", prof())).get("totalElements").asInt());
        assertEquals(1, ok(get("/banco-questoes").param("assunto", "capitais").header("Authorization", prof())).get("totalElements").asInt());
        assertEquals(0, ok(get("/banco-questoes").param("disciplinaId", UUID.randomUUID().toString()).header("Authorization", prof())).get("totalElements").asInt());

        JsonNode filtros = ok(get("/banco-questoes/filtros").header("Authorization", prof()));
        assertTrue(filtros.get("tags").toString().contains("geografia"));

        mvc.perform(patch("/banco-questoes/" + id).header("Authorization", prof()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"dificuldade\": \"IMPOSSIVEL\"}")).andExpect(status().is4xxClientError());

        // paginação
        JsonNode p = ok(get("/banco-questoes").param("size", "2").param("page", "2").header("Authorization", prof()));
        assertEquals(1, p.get("items").size());
        assertEquals(3, p.get("totalPages").asInt());

        // copiar para outra lista (cópia independente; duplicata é ignorada)
        UUID destino = UUID.fromString(ok(post("/listas/disciplina/" + disciplinaId)
                .param("titulo", "Reforço").param("professorId", seed.get("professorId").asText())
                .header("Authorization", prof())).get("id").asText());
        JsonNode r1 = ok(postJson("/banco-questoes/adicionar-a-lista", prof(), Map.of("listaId", destino, "questaoIds", List.of(id))));
        assertEquals(1, r1.get("adicionadas").asInt());
        JsonNode r2 = ok(postJson("/banco-questoes/adicionar-a-lista", prof(), Map.of("listaId", destino, "questaoIds", List.of(id))));
        assertEquals(0, r2.get("adicionadas").asInt());
        assertEquals(1, r2.get("ignoradas").asInt());
        assertEquals(1, ok(get("/listas/" + destino + "/questoes").header("Authorization", prof())).size());
        assertEquals(5, ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof())).size());
    }

    // ------------------------------------------------------------------ FE-205 / FE-206

    @Test
    void configuracaoDaListaValidaEBloqueiaRespostasForaDaJanela() throws Exception {
        mvc.perform(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abreEm\":\"2030-01-02T10:00:00Z\",\"fechaEm\":\"2030-01-01T10:00:00Z\"}")).andExpect(status().isBadRequest());
        mvc.perform(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"maxTentativas\": 0}")).andExpect(status().isBadRequest());
        mvc.perform(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"regraNota\": \"MEDIA\"}")).andExpect(status().isBadRequest());

        String futuro = Instant.now().plus(2, ChronoUnit.DAYS).toString();
        JsonNode cfg = ok(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"abreEm\":\"" + futuro + "\",\"maxTentativas\":3,\"regraNota\":\"ULTIMA\",\"tempoLimiteMinutos\":30,"
                        + "\"embaralharQuestoes\":true,\"embaralharAlternativas\":true}"));
        assertEquals(3, cfg.get("maxTentativas").asInt());
        assertEquals("ULTIMA", cfg.get("regraNota").asText());
        assertTrue(cfg.get("embaralharQuestoes").asBoolean());

        // ainda não abriu: aluno não consegue responder
        int qid = ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof())).get(0).get("id").asInt();
        mvc.perform(postJson("/enviaresposta", token("aluno2@mock.com"), Map.of(
                "estudanteId", aluno2.getId(), "questaoId", qid, "alternativa", 0, "listaId", listaId)))
                .andExpect(status().isBadRequest());

        // aberta e dentro do prazo: responde e a visão traz as regras e o prazo final do tempo limite
        ok(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"maxTentativas\":2,\"tempoLimiteMinutos\":30,\"embaralharQuestoes\":true,\"embaralharAlternativas\":true}"));
        mvc.perform(postJson("/enviaresposta", token("aluno2@mock.com"), Map.of(
                "estudanteId", aluno2.getId(), "questaoId", qid, "alternativa", 0, "listaId", listaId)))
                .andExpect(status().isOk());
        JsonNode visao = ok(get("/listas/" + listaId + "/estudantes/" + aluno2.getId() + "/visao")
                .header("Authorization", token("aluno2@mock.com")));
        assertTrue(visao.at("/config/aberta").asBoolean());
        assertFalse(visao.at("/config/prazoFinal").isNull());
        assertEquals(2, visao.at("/config/maxTentativas").asInt());

        // embaralhado: mesmas questões e alternativas, ordem estável por aluno
        JsonNode a = ok(get("/listas/" + listaId + "/questoes").header("Authorization", token("aluno2@mock.com")));
        JsonNode b = ok(get("/listas/" + listaId + "/questoes").header("Authorization", token("aluno2@mock.com")));
        assertEquals(5, a.size());
        assertEquals(a.toString(), b.toString());
        assertTrue(a.get(0).get("alternativas").get(0).has("ordem"));
        assertTrue(a.get(0).get("gabarito").isNull(), "aluno nunca recebe o gabarito");

        // fechada: nenhuma resposta nova
        ok(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaEm\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\"}"));
        mvc.perform(postJson("/enviaresposta", token("aluno2@mock.com"), Map.of(
                "estudanteId", aluno2.getId(), "questaoId", qid, "alternativa", 1, "listaId", listaId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tentativasRespeitamOLimiteEARegraDaNota() throws Exception {
        ok(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"maxTentativas\":2,\"regraNota\":\"MAIOR\"}"));
        // aluno1 já respondeu as 5 no seed; finaliza (1ª tentativa)
        ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/finalizar").header("Authorization", token("aluno1@mock.com")));
        double primeira = notaDe(aluno1);

        JsonNode nova = ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/nova-tentativa")
                .header("Authorization", token("aluno1@mock.com")));
        assertFalse(nova.get("finalizada").asBoolean());
        // com a regra "maior nota" a nota segue valendo a melhor enquanto refaz
        assertEquals(primeira, notaDe(aluno1), 0.001);

        int qid = ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof())).get(0).get("id").asInt();
        em.flush();
        em.clear();
        for (JsonNode q : ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof()))) {
            mvc.perform(postJson("/enviaresposta", token("aluno1@mock.com"), Map.of(
                    "estudanteId", aluno1.getId(), "questaoId", q.get("id").asInt(), "alternativa", 4, "listaId", listaId)))
                    .andExpect(status().isOk());
        }
        ok(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/finalizar").header("Authorization", token("aluno1@mock.com")));
        assertEquals(primeira, notaDe(aluno1), 0.001);

        // limite de 2 tentativas atingido
        mvc.perform(post("/listas/" + listaId + "/estudantes/" + aluno1.getId() + "/nova-tentativa")
                .header("Authorization", token("aluno1@mock.com"))).andExpect(status().isBadRequest());
        assertTrue(qid > 0);
    }

    @Test
    void duplicarCriaRascunhoInvisivelAoAlunoEPublicarLiberaEAvisa() throws Exception {
        JsonNode copia = ok(postJson("/listas/" + listaId + "/duplicar", prof(), Map.of(
                "disciplinaId", disciplinaId, "titulo", "Lista 1 - 2027/1",
                "abreEm", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                "fechaEm", Instant.now().plus(8, ChronoUnit.DAYS).toString())));
        UUID copiaId = UUID.fromString(copia.get("id").asText());
        assertEquals("RASCUNHO", copia.get("status").asText());
        assertEquals("Lista 1 - 2027/1", copia.get("titulo").asText());
        assertEquals(5, ok(get("/listas/" + copiaId + "/questoes").header("Authorization", prof())).size());

        // aluno não vê rascunho e não consegue responder
        JsonNode listasAluno = ok(get("/listas/estudante/" + aluno1.getId()));
        for (JsonNode l : listasAluno) assertNotEquals(copiaId.toString(), l.get("id").asText());
        // o original não muda
        assertEquals(5, ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof())).size());

        // destino fora das disciplinas do professor é recusado
        mvc.perform(postJson("/listas/" + listaId + "/duplicar", prof(), Map.of("disciplinaId", UUID.randomUUID())))
                .andExpect(status().isNotFound());

        JsonNode pub = ok(post("/listas/" + copiaId + "/publicar").header("Authorization", prof()));
        assertEquals("PUBLICADA", pub.get("status").asText());
        em.flush();
        em.clear();
        boolean viu = false;
        for (JsonNode l : ok(get("/listas/estudante/" + aluno1.getId()))) viu |= copiaId.toString().equals(l.get("id").asText());
        assertTrue(viu);
        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n ->
                n.getUsuarioId().equals(aluno1.getId()) && n.getTipo() == TipoNotificacao.LISTA_NOVA
                        && n.getLink().contains(copiaId.toString())));
    }

    // ------------------------------------------------------------------ FE-203

    @Test
    void reabrirListaAtualizaPrazoEAvisaOsAlunos() throws Exception {
        mvc.perform(postJson("/listas/" + listaId + "/reabrir", prof(), Map.of(
                "fechaEm", Instant.now().minus(1, ChronoUnit.DAYS).toString()))).andExpect(status().isBadRequest());

        JsonNode r = ok(postJson("/listas/" + listaId + "/reabrir", prof(), Map.of(
                "fechaEm", Instant.now().plus(7, ChronoUnit.DAYS).toString(), "permitirNovaTentativa", true)));
        assertEquals(2, r.get("notificados").asInt());
        assertEquals(2, r.at("/lista/maxTentativas").asInt());
        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n ->
                n.getUsuarioId().equals(aluno2.getId()) && n.getTipo() == TipoNotificacao.PRAZO));
    }

    @Test
    void assuntosFracosEListaDeReforco() throws Exception {
        // dá um assunto às questões já respondidas
        JsonNode qs = ok(get("/listas/" + listaId + "/questoes").header("Authorization", prof()));
        for (JsonNode q : qs) {
            ok(put("/listas/" + listaId + "/questoes/" + q.get("id").asInt()).header("Authorization", prof())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of(
                            "enunciado", q.get("enunciado").asText(),
                            "alternativas", List.of("a", "b", "c", "d", "e"), "gabarito", q.get("gabarito").isNull() ? 0 : q.get("gabarito").asInt(),
                            "assunto", q.get("id").asInt() % 2 == 0 ? "Sintaxe" : "Ortografia"))));
        }
        em.flush();
        em.clear();
        JsonNode fracos = ok(get("/disciplinas/" + disciplinaId + "/assuntos-fracos").header("Authorization", prof()));
        assertTrue(fracos.size() >= 1);
        assertTrue(fracos.get(0).get("taxaAcerto").asDouble() <= fracos.get(fracos.size() - 1).get("taxaAcerto").asDouble());

        String assunto = fracos.get(0).get("assunto").asText();
        mvc.perform(postJson("/listas/reforco", prof(), Map.of("disciplinaId", disciplinaId, "titulo", "Reforço",
                "assuntos", List.of("Inexistente"), "publicar", true))).andExpect(status().isBadRequest());

        JsonNode ref = ok(postJson("/listas/reforco", prof(), Map.of("disciplinaId", disciplinaId, "titulo", "Reforço de " + assunto,
                "assuntos", List.of(assunto), "estudanteIds", List.of(aluno2.getId()), "quantidadePorAssunto", 2, "publicar", true)));
        assertEquals(1, ref.get("alunos").asInt());
        assertTrue(ref.get("questoes").asInt() >= 1 && ref.get("questoes").asInt() <= 2);
        assertEquals("PUBLICADA", ref.at("/lista/status").asText());
        UUID refId = UUID.fromString(ref.at("/lista/id").asText());
        em.flush();
        em.clear();
        // só o aluno escolhido recebe a lista
        boolean a2 = false;
        for (JsonNode l : ok(get("/listas/estudante/" + aluno2.getId()))) a2 |= refId.toString().equals(l.get("id").asText());
        boolean a1 = false;
        for (JsonNode l : ok(get("/listas/estudante/" + aluno1.getId()))) a1 |= refId.toString().equals(l.get("id").asText());
        assertTrue(a2);
        assertFalse(a1);

        // como rascunho: ninguém recebe
        JsonNode rasc = ok(postJson("/listas/reforco", prof(), Map.of("disciplinaId", disciplinaId, "titulo", "Rascunho",
                "assuntos", List.of(assunto), "publicar", false)));
        assertEquals("RASCUNHO", rasc.at("/lista/status").asText());
    }

    @Test
    void avisarAlunosValidaEscopoEMensagem() throws Exception {
        JsonNode r = ok(postJson("/professor/avisar-alunos", prof(), Map.of(
                "estudanteIds", List.of(aluno1.getId(), aluno2.getId()), "mensagem", "Vamos conversar sobre a lista?")));
        assertEquals(2, r.get("enviados").asInt());
        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n ->
                n.getUsuarioId().equals(aluno1.getId()) && n.getMensagem().startsWith("Vamos conversar")));
        mvc.perform(postJson("/professor/avisar-alunos", prof(), Map.of("estudanteIds", List.of(aluno1.getId()), "mensagem", " ")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/professor/avisar-alunos", prof(), Map.of("estudanteIds", List.of(UUID.randomUUID()), "mensagem", "oi")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ FE-204 / FE-207

    @Test
    void alunosEmRiscoMostramCriteriosEPerfilReaproveitaEvolucao() throws Exception {
        // nenhum aluno acessou o app, então ambos estão sem acesso
        JsonNode painel = ok(get("/professor/alunos-em-risco").header("Authorization", prof()));
        assertEquals(6.0, painel.at("/criterios/mediaMinima").asDouble(), 0.001);
        assertEquals(14, painel.at("/criterios/diasSemAcesso").asInt());
        assertEquals(2, painel.get("totalAlunos").asInt());
        assertEquals(2, painel.get("items").size());
        assertTrue(painel.get("items").get(0).get("motivos").toString().contains("SEM_ACESSO"));

        // acesso registrado ao usar o app como aluno tira o motivo
        ok(get("/listas/estudante/" + aluno1.getId()).header("Authorization", token("aluno1@mock.com")));
        JsonNode depois = ok(get("/professor/alunos-em-risco").header("Authorization", prof()));
        assertEquals(1, depois.get("items").size());
        assertEquals(2, ok(get("/professor/alunos-em-risco").param("todos", "true").header("Authorization", prof())).get("items").size());
        // critério configurável
        assertEquals(1, ok(get("/professor/alunos-em-risco").param("diasSemAcesso", "1").param("todos", "false")
                .header("Authorization", prof())).get("items").size());

        // atrasada: prazo vencido e sem entrega
        ok(put("/listas/" + listaId + "/configuracao").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fechaEm\":\"" + Instant.now().minus(2, ChronoUnit.DAYS) + "\"}"));
        em.flush();
        em.clear();
        JsonNode atrasados = ok(get("/professor/alunos-em-risco").param("todos", "true").header("Authorization", prof()));
        for (JsonNode a : atrasados.get("items")) assertEquals(1, a.get("listasAtrasadas").asInt());

        JsonNode perfil = ok(get("/professor/alunos/" + aluno1.getId() + "/perfil").header("Authorization", prof()));
        assertEquals("Aluno Um", perfil.get("nome").asText());
        assertEquals(1, perfil.get("disciplinas").size());
        assertEquals(1, perfil.get("resumo").get("atrasadas").asInt());
        assertEquals("ATRASADA", perfil.get("historico").get(0).get("status").asText());
        assertTrue(perfil.get("acessos").size() >= 1);
        assertTrue(perfil.get("evolucao").has("notas") && perfil.get("evolucao").has("assuntos"));
        mvc.perform(get("/professor/alunos/" + UUID.randomUUID() + "/perfil").header("Authorization", prof()))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ FE-209

    @Test
    void muralPublicaFixaExcluiEControlaAcesso() throws Exception {
        String url = "/disciplinas/" + disciplinaId + "/avisos";
        MockMultipartFile anexo = new MockMultipartFile("anexo", "edital.pdf", "application/pdf", "%PDF-1.4 teste".getBytes());
        JsonNode a = json.readTree(mvc.perform(multipart(url).file(anexo).header("Authorization", prof())
                .param("titulo", "Prova sexta").param("texto", "Levar **caneta**").param("fixado", "false"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        String id = a.get("id").asText();
        assertEquals("edital.pdf", a.at("/anexo/nome").asText());

        JsonNode b = json.readTree(mvc.perform(multipart(url).header("Authorization", prof())
                .param("titulo", "Fixado").param("texto", "Importante").param("fixado", "true"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        // aluno matriculado lê, com o fixado no topo, e é notificado
        JsonNode lista = ok(get(url).header("Authorization", token("aluno1@mock.com")));
        assertEquals(2, lista.size());
        assertEquals(b.get("id").asText(), lista.get(0).get("id").asText());
        assertTrue(notificacaoRepository.findAll().stream().anyMatch(n ->
                n.getUsuarioId().equals(aluno1.getId()) && n.getMensagem().contains("Prova sexta")));

        byte[] baixado = mvc.perform(get("/avisos/" + id + "/anexo").header("Authorization", token("aluno1@mock.com")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF-1.4 teste", new String(baixado, StandardCharsets.UTF_8));

        // aluno não publica, não fixa, não exclui
        mvc.perform(multipart(url).header("Authorization", token("aluno1@mock.com")).param("titulo", "x").param("texto", "y"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/avisos/" + id + "/fixar").header("Authorization", token("aluno1@mock.com"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"fixado\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete("/avisos/" + id).header("Authorization", token("aluno1@mock.com"))).andExpect(status().isForbidden());
        // sem token não lê
        mvc.perform(get(url)).andExpect(status().isUnauthorized());

        // anexo inválido
        mvc.perform(multipart(url).file(new MockMultipartFile("anexo", "x.exe", "application/octet-stream", new byte[]{1}))
                .header("Authorization", prof()).param("titulo", "t").param("texto", "x")).andExpect(status().isBadRequest());
        mvc.perform(multipart(url).header("Authorization", prof()).param("titulo", " ").param("texto", "x"))
                .andExpect(status().isBadRequest());

        JsonNode fixado = ok(patch("/avisos/" + id + "/fixar").header("Authorization", prof())
                .contentType(MediaType.APPLICATION_JSON).content("{\"fixado\":true}"));
        assertTrue(fixado.get("fixado").asBoolean());
        mvc.perform(delete("/avisos/" + id).header("Authorization", prof())).andExpect(status().isNoContent());
        assertEquals(1, ok(get(url).header("Authorization", prof())).size());
    }

    // ------------------------------------------------------------------ FE-208

    @Test
    void exportacaoRejeitaPedidosInvalidos() throws Exception {
        mvc.perform(postJson("/exportacoes", prof(), Map.of("escopo", "TUDO", "alvoId", listaId, "formato", "CSV")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/exportacoes", prof(), Map.of("escopo", "LISTA", "alvoId", listaId, "formato", "XLS")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/exportacoes", prof(), Map.of("escopo", "LISTA", "alvoId", UUID.randomUUID(), "formato", "CSV")))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/exportacoes", prof(), Map.of("escopo", "LISTA", "alvoId", listaId, "formato", "CSV",
                "colunas", List.of("inexistente")))).andExpect(status().isBadRequest());
        JsonNode cols = ok(get("/exportacoes/colunas").header("Authorization", prof()));
        assertTrue(cols.size() >= 8);

        JsonNode criada = json.readTree(mvc.perform(postJson("/exportacoes", prof(), Map.of(
                        "escopo", "DISCIPLINA", "alvoId", disciplinaId, "formato", "PDF", "colunas", List.of("aluno", "nota"))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        assertEquals("PROCESSANDO", criada.get("status").asText());
        // arquivo ainda não pronto não pode ser baixado
        mvc.perform(get("/exportacoes/" + criada.get("id").asText() + "/arquivo").header("Authorization", prof()))
                .andExpect(status().is4xxClientError());
    }
}
