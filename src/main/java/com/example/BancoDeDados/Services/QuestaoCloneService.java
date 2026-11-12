package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.QuestaoAlternativa;
import com.example.BancoDeDados.Model.QuestaoImagem;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Copia uma questão para outra lista. Cada questão pertence a uma única lista, então reutilizar (banco de
 * questões, duplicar lista, lista de reforço) sempre cria uma cópia independente, com as imagens duplicadas.
 */
@Service
public class QuestaoCloneService {

    @Autowired
    private QuestaoRepositores questaoRepositores;

    @Transactional
    public Questao clonar(Questao origem, Lista destino) {
        Questao copia = Questao.builder()
                .cabecalho(origem.getCabecalho())
                .enunciado(origem.getEnunciado())
                .gabarito(origem.getGabarito())
                .assunto(origem.getAssunto())
                .explicacao(origem.getExplicacao())
                .tipo(origem.getTipo())
                .rubrica(origem.getRubrica())
                .dificuldade(origem.getDificuldade())
                .tags(new LinkedHashSet<>(origem.getTags()))
                .lista(destino)
                .build();
        for (QuestaoAlternativa a : origem.getAlternativas()) {
            copia.addAlternativa(a.getTexto(), a.getOrdem());
        }
        for (QuestaoImagem img : origem.getImagens()) {
            copia.getImagens().add(QuestaoImagem.builder()
                    .questao(copia)
                    .nomeArquivo("copia-" + UUID.randomUUID().toString().substring(0, 8) + "-" + img.getNomeArquivo())
                    .dadosImagem(img.getDadosImagem())
                    .tipoMime(img.getTipoMime())
                    .tamanhoBytes(img.getTamanhoBytes())
                    .ordem(img.getOrdem())
                    .textoOcr(img.getTextoOcr())
                    .exibirNoEnunciado(img.getExibirNoEnunciado())
                    .exibirNasAlternativas(img.getExibirNasAlternativas())
                    .etag(img.getEtag())
                    .build());
        }
        return questaoRepositores.save(copia);
    }
}
