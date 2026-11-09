package com.example.BancoDeDados;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.Security.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardAlunoEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TokenService tokenService;
    @Autowired AccountRepository accountRepository;
    @Autowired EstudanteRepositores estudanteRepository;
    @Autowired ListaRepository listaRepository;
    @Autowired QuestaoRepositores questaoRepository;
    @Autowired RespostaEstudantesRepository respostaRepository;
    @Autowired ListaEstudanteNotaRepository notaListaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired NotaEventoRepository notaEventoRepository;

    JsonNode seed;
    Estudante aluno1;
    Estudante aluno2;

    @BeforeEach
    void seed() throws Exception {
        String body = mvc.perform(post("/seed/popular")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        seed = json.readTree(body);
        aluno1 = estudanteRepository.findByEmail("aluno1@mock.com").orElseThrow();
        aluno2 = estudanteRepository.findByEmail("aluno2@mock.com").orElseThrow();
    }

    private String token(String email) {
        return "Bearer " + tokenService.gerarToken(accountRepository.findByEmail(email).orElseThrow());
    }

    private JsonNode dashboard(Estudante dono, String tokenEmail, String tz) throws Exception {
        var req = get("/estudantes/" + dono.getId() + "/dashboard").header("Authorization", token(tokenEmail));
        if (tz != null) req = req.header("X-Timezone", tz);
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/estudantes/" + aluno1.getId() + "/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void alunoNaoVeDashboardDeOutroAluno() throws Exception {
        mvc.perform(get("/estudantes/" + aluno2.getId() + "/dashboard").header("Authorization", token("aluno1@mock.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void professorNaoAcessa() throws Exception {
        mvc.perform(get("/estudantes/" + aluno1.getId() + "/dashboard").header("Authorization", token("professor@mock.com")))
                .andExpect(status().isForbidden());
    }

    @Test
    void fusoInvalidoRetorna400() throws Exception {
        mvc.perform(get("/estudantes/" + aluno1.getId() + "/dashboard")
                        .header("Authorization", token("aluno1@mock.com")).header("X-Timezone", "Marte/Olympus"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retornaTotalDisciplinasSequenciaEFusoPadrao() throws Exception {
        JsonNode d = dashboard(aluno1, "aluno1@mock.com", null);
        assertEquals(1, d.get("totalDisciplinas").asInt());
        assertEquals("America/Sao_Paulo", d.get("fusoHorario").asText());
        assertEquals(1, d.at("/sequencia/diasConsecutivos").asInt()); // seed respondeu hoje
        assertTrue(d.at("/sequencia/ultimaAtividadeEm").asText().matches(".*-03:00$"),
                d.at("/sequencia/ultimaAtividadeEm").asText());
        assertEquals(0, d.get("emAndamento").size());
        assertEquals(0, d.get("proximasEntregas").size());
    }

    @Test
    void headerXTimezoneMudaOffsetDosInstantes() throws Exception {
        JsonNode d = dashboard(aluno1, "aluno1@mock.com", "Asia/Tokyo");
        assertEquals("Asia/Tokyo", d.get("fusoHorario").asText());
        assertTrue(d.at("/sequencia/ultimaAtividadeEm").asText().endsWith("+09:00"));
    }

    @Test
    void fusoSalvoNoAlunoEhUsadoSemHeader() throws Exception {
        aluno1.setFusoHorario("Europe/Lisbon");
        estudanteRepository.saveAndFlush(aluno1);
        assertEquals("Europe/Lisbon", dashboard(aluno1, "aluno1@mock.com", null).get("fusoHorario").asText());
    }

    @Test
    void emAndamentoLimitaEm10MaisRecentePrimeiro() throws Exception {
        Lista seedLista = listaRepository.findById(UUID.fromString(seed.get("listaId").asText())).orElseThrow();
        List<Lista> criadas = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Lista l = new Lista();
            l.setTitulo("Parcial " + i);
            l.setProfessor(seedLista.getProfessor());
            l.setDisciplina(seedLista.getDisciplina());
            l.setQuestoes(new ArrayList<>());
            l = listaRepository.save(l);
            Questao q = new Questao();
            q.setEnunciado("Q" + i);
            q.setGabarito(0);
            q.setLista(l);
            q = questaoRepository.save(q);
            respostaRepository.saveAndFlush(new RespostaEstudantes(null, q, true, 0, aluno2));
            notaListaRepository.save(nota(l, aluno2, 1, 3, false));
            criadas.add(l);
            Thread.sleep(5); // garante atualizadoEm crescente
        }
        // Não deve aparecer: lista de outro aluno
        notaListaRepository.save(nota(criadas.get(0), aluno1, 1, 3, false));

        JsonNode d = dashboard(aluno2, "aluno2@mock.com", null);
        JsonNode lista = d.get("emAndamento");
        assertEquals(10, lista.size());
        assertEquals("Parcial 11", lista.get(0).get("titulo").asText());
        assertEquals("Parcial 2", lista.get(9).get("titulo").asText());
        assertEquals(1, lista.get(0).get("questoesRespondidas").asInt());
        assertEquals(3, lista.get(0).get("totalQuestoes").asInt());
        assertEquals("Língua Portuguesa", lista.get(0).get("disciplinaNome").asText());
        assertTrue(lista.get(0).get("ultimaAtividadeEm").asText().endsWith("-03:00"));
    }

    @Test
    void listaCompletaMasNaoFinalizadaContaComoEmAndamentoEFinalizadaNao() throws Exception {
        Lista seedLista = listaRepository.findById(UUID.fromString(seed.get("listaId").asText())).orElseThrow();
        notaListaRepository.save(nota(seedLista, aluno2, 5, 5, false));
        JsonNode d = dashboard(aluno2, "aluno2@mock.com", null).get("emAndamento");
        assertEquals(1, d.size());
        assertEquals(5, d.get(0).get("questoesRespondidas").asInt());
        assertEquals(5, d.get(0).get("totalQuestoes").asInt());

        ListaEstudanteNota n = notaListaRepository.findByListaIdAndEstudanteId(seedLista.getId(), aluno2.getId()).orElseThrow();
        n.setFinalizada(true);
        notaListaRepository.saveAndFlush(n);
        assertEquals(0, dashboard(aluno2, "aluno2@mock.com", null).get("emAndamento").size());
    }

    @Test
    void ultimasNotasTrazSoListasFinalizadasComNota() throws Exception {
        Lista seedLista = listaRepository.findById(UUID.fromString(seed.get("listaId").asText())).orElseThrow();
        ListaEstudanteNota n = nota(seedLista, aluno2, 5, 5, false);
        n.setNota(new BigDecimal("8.50"));
        notaListaRepository.saveAndFlush(n);
        assertEquals(0, dashboard(aluno2, "aluno2@mock.com", null).get("ultimasNotas").size());

        n.setFinalizada(true);
        notaListaRepository.saveAndFlush(n);
        JsonNode notas = dashboard(aluno2, "aluno2@mock.com", null).get("ultimasNotas");
        assertEquals(1, notas.size());
        assertEquals(seedLista.getId().toString(), notas.get(0).get("listaId").asText());
        assertEquals(8.5, notas.get(0).get("nota").asDouble(), 0.001);
        assertEquals(10.0, notas.get(0).get("notaMaxima").asDouble(), 0.001);
    }

    @Test
    void proximasEntregasSao5PendentesFuturasOrdenadasComOffset() throws Exception {
        Lista seedLista = listaRepository.findById(UUID.fromString(seed.get("listaId").asText())).orElseThrow();
        LocalDateTime base = LocalDateTime.now().plusDays(1).withNano(0).withSecond(0);
        // 7 futuros (criados fora de ordem), 1 passado, 1 já entregue
        int[] dias = {5, 1, 7, 3, 2, 6, 4};
        for (int dia : dias) {
            evento("Futuro D+" + dia, base.plusDays(dia), seedLista, NotaEvento.StatusEntrega.PENDENTE);
        }
        evento("Passado", LocalDateTime.now().minusDays(2), seedLista, NotaEvento.StatusEntrega.PENDENTE);
        evento("Entregue", base.plusHours(1), seedLista, NotaEvento.StatusEntrega.ENTREGUE);

        JsonNode itens = dashboard(aluno1, "aluno1@mock.com", "America/Sao_Paulo").get("proximasEntregas");
        assertEquals(5, itens.size());
        assertEquals("Futuro D+1", itens.get(0).get("titulo").asText());
        assertEquals("Futuro D+5", itens.get(4).get("titulo").asText());
        // Evento.data (sem fuso) é lida como hora local do aluno: mesmo relógio, offset -03:00
        String prazo = itens.get(0).get("prazo").asText();
        assertEquals(base.plusDays(1).toLocalDate() + "T" + String.format("%02d:%02d", base.getHour(), base.getMinute()),
                prazo.substring(0, 16));
        assertTrue(prazo.endsWith("-03:00"), prazo);
        assertEquals("Língua Portuguesa", itens.get(0).get("disciplinaNome").asText());

        // Mesmo relógio, outro fuso => outro offset
        JsonNode tokyo = dashboard(aluno1, "aluno1@mock.com", "Asia/Tokyo").get("proximasEntregas");
        assertTrue(tokyo.get(0).get("prazo").asText().endsWith("+09:00"));
    }

    private ListaEstudanteNota nota(Lista l, Estudante e, int respondidas, int total, boolean finalizada) {
        ListaEstudanteNota n = new ListaEstudanteNota();
        n.setLista(l);
        n.setEstudante(e);
        n.setNota(BigDecimal.ZERO);
        n.setPorcentagemAcertos(BigDecimal.ZERO);
        n.setQuestoesRespondidas(respondidas);
        n.setQuestoesCorretas(0);
        n.setTotalQuestoes(total);
        n.setFinalizada(finalizada);
        return n;
    }

    private void evento(String titulo, LocalDateTime data, Lista base, NotaEvento.StatusEntrega status) {
        Evento ev = new Evento();
        ev.setTitulo(titulo);
        ev.setData(data);
        ev.setNotaMaxima(10.0);
        ev.setDisciplina(base.getDisciplina());
        ev.setProfessor(base.getProfessor());
        ev = eventoRepository.save(ev);
        NotaEvento ne = new NotaEvento();
        ne.setEvento(ev);
        ne.setEstudante(aluno1);
        ne.setProfessor(base.getProfessor());
        ne.setStatusEntrega(status);
        notaEventoRepository.save(ne);
    }
}
