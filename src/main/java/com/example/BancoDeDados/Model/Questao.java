package com.example.BancoDeDados.Model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questao", indexes = {
    @Index(name = "idx_questao_lista", columnList = "lista_id"),
    @Index(name = "idx_questao_gabarito", columnList = "gabarito")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Questao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(columnDefinition = "TEXT")
    private String cabecalho;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String enunciado;

    // null quando a questão foi importada sem gabarito
    @Column
    private Integer gabarito;

    // Assunto/tópico da questão (texto livre), usado na revisão do aluno
    @Column(length = 255)
    private String assunto;

    // Explicação do gabarito, exibida ao aluno só quando o gabarito está liberado
    @Column(columnDefinition = "TEXT")
    private String explicacao;

    // FE-201: "OBJETIVA" (padrão; null conta como objetiva) ou "ABERTA" (resposta escrita, corrigida pelo professor)
    @Column(length = 20)
    private String tipo;

    // FE-201: critérios de correção de uma questão aberta (visível só ao professor)
    @Column(columnDefinition = "TEXT")
    private String rubrica;

    // FE-202: "FACIL" | "MEDIA" | "DIFICIL" (null = não classificada)
    @Column(length = 10)
    private String dificuldade;

    // FE-202: etiquetas livres para busca no banco de questões
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "questao_tags", joinColumns = @JoinColumn(name = "questao_id"))
    @Column(name = "tag", length = 60)
    @Builder.Default
    private java.util.Set<String> tags = new java.util.LinkedHashSet<>();

    public boolean isAberta() {
        return "ABERTA".equals(tipo);
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lista_id", nullable = false)
    private Lista lista;

    //  Relacionamento 1:N com QuestaoAlternativa
    @OneToMany(mappedBy = "questao", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    @BatchSize(size = 50)
    @Builder.Default
    private List<QuestaoAlternativa> alternativas = new ArrayList<>();

    // Relacionamento com imagens da questão
    @OneToMany(mappedBy = "questao", fetch = FetchType.LAZY,
               cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordem ASC")
    @Builder.Default
    private List<QuestaoImagem> imagens = new ArrayList<>();

    // Relacionamento com respostas de estudantes
    @OneToMany(mappedBy = "questao", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RespostaEstudantes> respostas = new ArrayList<>();

    //  Helper method para adicionar alternativa
    public void addAlternativa(String texto, Integer ordem) {
        QuestaoAlternativa alt = QuestaoAlternativa.builder()
            .questao(this)
            .texto(texto)
            .ordem(ordem)
            .build();
        alternativas.add(alt);
    }

    //  Helper method para obter alternativas como List<String> (compatibilidade)
    public List<String> getAlternativasTexto() {
        return alternativas.stream()
            .sorted((a, b) -> a.getOrdem().compareTo(b.getOrdem()))
            .map(QuestaoAlternativa::getTexto)
            .toList();
    }

    //  Helper method para setar alternativas de List<String> (compatibilidade)
    public void setAlternativasTexto(List<String> textos) {
        this.alternativas.clear();
        for (int i = 0; i < textos.size(); i++) {
            addAlternativa(textos.get(i), i);
        }
    }
}
