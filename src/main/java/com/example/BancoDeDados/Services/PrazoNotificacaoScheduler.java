package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.Model.TipoNotificacao;
import com.example.BancoDeDados.Repositores.EventoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Avisa os alunos de atividades cujo prazo vence nas próximas 24 horas (uma vez por atividade). */
@Component
public class PrazoNotificacaoScheduler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm");

    @Autowired
    private EventoRepository eventoRepository;

    @Autowired
    private NotificacaoService notificacaoService;

    @Scheduled(fixedDelayString = "${notificacoes.prazo.intervalo-ms:3600000}", initialDelay = 60000)
    @Transactional
    public void notificarPrazosProximos() {
        LocalDateTime agora = LocalDateTime.now();
        List<Evento> eventos = eventoRepository.findByDataBetween(agora, agora.plusHours(24));
        for (Evento evento : eventos) {
            String link = "/atividades/" + evento.getId();
            String msg = evento.getTitulo() + " vence em " + evento.getData().format(FMT) + ".";
            for (NotaEvento ne : evento.getNotasEstudante()) {
                if (ne.getEstudante() == null) continue;
                notificacaoService.criarSeNaoExistir(ne.getEstudante().getId(), TipoNotificacao.PRAZO,
                        "Prazo se aproximando", msg, link);
            }
        }
    }
}
