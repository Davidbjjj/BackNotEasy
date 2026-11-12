package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** FE-309: identidade visual e regras padrão de uma instituição. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "configuracao_instituicao")
public class ConfiguracaoInstituicao {
    @Id
    @Column(name = "instituicao_id", columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID instituicaoId;

    /** #RRGGBB */
    @Column(name = "cor_principal", length = 7)
    private String corPrincipal;

    @Column(name = "logo_tipo", length = 40)
    private String logoTipo;

    @Column(name = "logo_dados", columnDefinition = "bytea")
    private byte[] logoDados;

    @Column(name = "tentativas_padrao")
    private Integer tentativasPadrao;

    @Column(name = "tempo_limite_padrao_min")
    private Integer tempoLimitePadraoMin;

    @Column(name = "gabarito_liberado_padrao", nullable = false)
    private boolean gabaritoLiberadoPadrao = false;
}
