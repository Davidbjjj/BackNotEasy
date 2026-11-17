package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/serviceIA")
public class IAController {

    @Autowired
    private PDFIAService PDFIAService;

    @Autowired
    private IAService IAService;

    @Autowired
    private TratarRespostaIAService tratarRespostaIA;

    @Autowired
    private QuestaoService questaoService;

    @Autowired
    private ExtratorService extratorService;

    @Autowired
    private JsonIAService json;

    @Autowired
    private ListaService listaService;

    @Autowired
    private QuestaoComImagemIAService questaoComImagemIAService;

    @Autowired
    private ImagemQuestaoService imagemQuestaoService;

    @Autowired
    private ImagemQuestaoAssociadorService imagemAssociador;

    @Autowired
    private com.example.BancoDeDados.service.OcrService ocrService;

    /**
     * Novo endpoint: Processa PDF com OCR e envia para IA com contexto das imagens
     * POST /serviceIA/{listaId}/processar-pdf-com-imagens
     */
    @PostMapping("/{listaId}/processar-pdf-com-imagens")
    public ResponseEntity<Map<String, Object>> processarPdfComImagens(
            @PathVariable UUID listaId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "filePrefix", required = false) String filePrefix) {

        try {
            if (listaId == null) {
                return ResponseEntity.badRequest().body(Map.of("erro", "ID da lista inválido"));
            }

            System.out.println("Processando PDF com OCR e IA: " + file.getOriginalFilename());

            // Processar PDF com OCR e enviar para IA
            Map<String, Object> resultado = questaoComImagemIAService.processarPdfComImagensParaIA(file, filePrefix);

            // Extrair questões do resultado
            @SuppressWarnings("unchecked")
            List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> questoesDTO =
                (List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO>) resultado.get("questoesIA");

            // Extrair caminhos das imagens temporárias e textos OCR
            @SuppressWarnings("unchecked")
            List<String> arquivosTemp = (List<String>) resultado.get("arquivosTemporarios");
            @SuppressWarnings("unchecked")
            List<String> textosOcr = (List<String>) resultado.get("textosOcr");
            @SuppressWarnings("unchecked")
            List<com.example.BancoDeDados.service.PosicaoImagem> posicoesImagens =
                (List<com.example.BancoDeDados.service.PosicaoImagem>) resultado.get("posicoesImagens");
            @SuppressWarnings("unchecked")
            List<com.example.BancoDeDados.service.LinhaPdf> linhasPdf =
                (List<com.example.BancoDeDados.service.LinhaPdf>) resultado.get("linhasPdf");

            // ASSOCIAR IMAGENS ÀS QUESTÕES CORRETAS (antes de salvar: a imagem de alternativas vira texto)
            Map<Integer, List<Integer>> imagensPorQuestao = new HashMap<>();
            Set<Integer> imagensViraramTexto = new HashSet<>();
            Map<Integer, Integer> letraPorImagem = new HashMap<>();
            if (arquivosTemp != null && !arquivosTemp.isEmpty() && !questoesDTO.isEmpty()) {
                // PDF: associa pela posição da imagem na página; sem posição, cai no texto OCR
                boolean temPosicao = posicoesImagens != null && linhasPdf != null && !linhasPdf.isEmpty()
                    && posicoesImagens.stream().anyMatch(Objects::nonNull);
                imagensPorQuestao = temPosicao
                    ? imagemAssociador.agruparImagensPorPosicao(questoesDTO, linhasPdf, posicoesImagens)
                    : imagemAssociador.agruparImagensPorQuestao(textosOcr, questoesDTO.size());
                imagensViraramTexto = alternativasDasImagens(questoesDTO, imagensPorQuestao, textosOcr, arquivosTemp);
                letraPorImagem = figurasComoAlternativas(questoesDTO, imagensPorQuestao, arquivosTemp,
                        posicoesImagens, imagensViraramTexto);
            }

            // Converter DTO para Model e salvar
            List<Questao> questoes = converterDTOParaModel(questoesDTO);
            listaService.salvarQuestoesComLista(questoes, listaId);

            int totalImagensSalvas = 0;
            if (arquivosTemp != null && !arquivosTemp.isEmpty() && !questoes.isEmpty()) {
                // Salvar apenas as imagens relevantes para cada questão
                for (int i = 0; i < questoes.size(); i++) {
                    Questao questao = questoes.get(i);
                    int numeroQuestao = i + 1; // Questões são 1-based

                    List<Integer> indicesImagens = imagensPorQuestao.get(numeroQuestao);

                    if (indicesImagens != null && !indicesImagens.isEmpty()) {
                        // Filtrar apenas as imagens desta questão
                        List<String> imagensDaQuestao = new ArrayList<>();
                        List<String> ocrsRelacionados = new ArrayList<>();
                        List<Integer> letrasDasImagens = new ArrayList<>();

                        for (Integer indice : indicesImagens) {
                            if (indice < arquivosTemp.size() && !imagensViraramTexto.contains(indice)) {
                                imagensDaQuestao.add(arquivosTemp.get(indice));
                                ocrsRelacionados.add(textosOcr.get(indice));
                                letrasDasImagens.add(letraPorImagem.get(indice));
                            }
                        }

                        if (!imagensDaQuestao.isEmpty()) {
                            List<com.example.BancoDeDados.Model.QuestaoImagem> imagens =
                                imagemQuestaoService.salvarImagensQuestao(questao, imagensDaQuestao, ocrsRelacionados, letrasDasImagens);
                            totalImagensSalvas += imagens.size();

                            System.out.println("Questão " + numeroQuestao + ": " + imagens.size() + " imagem(ns) associada(s)");
                        } else {
                            System.out.println("Questão " + numeroQuestao + ": sem imagens");
                        }
                    }
                }
            }

            // Adicionar info de salvamento ao resultado
            resultado.put("listaId", listaId);
            resultado.put("questoesSalvas", questoes.size());
            resultado.put("imagensSalvas", totalImagensSalvas);
            resultado.put("mensagem", "Questões e imagens processadas com OCR e IA, salvas com sucesso!");

            // Remover dados temporários da resposta
            resultado.remove("arquivosTemporarios");
            resultado.remove("textosOcr");
            resultado.remove("posicoesImagens");
            resultado.remove("linhasPdf");

            return ResponseEntity.ok(resultado);

        } catch (IOException e) {
            return ResponseEntity.status(500).body(Map.of(
                "erro", "Erro ao processar arquivo",
                "mensagem", e.getMessage()
            ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of(
                "erro", "Erro ao processar requisição",
                "mensagem", e.getMessage()
            ));
        }
    }

    /**
     * Questão sem alternativas no texto do PDF, mas com uma imagem que traz as alternativas escritas
     * (ex.: quadro "A) 135 000,00. B) ..."): usa o OCR dessa imagem como alternativas, desde que o
     * {@link AlternativasOcrParser} o considere confiável. Quando o OCR comum não serve porque as alternativas
     * têm expoentes (notação científica: "1·10⁴"), a imagem é lida de novo separando os expoentes
     * ({@link com.example.BancoDeDados.service.OcrService#lerAlternativasComExpoentes}). Devolve os índices
     * das imagens consumidas (que não precisam mais ser anexadas, pois agora são texto).
     */
    private Set<Integer> alternativasDasImagens(List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> questoesDTO,
                                                Map<Integer, List<Integer>> imagensPorQuestao,
                                                List<String> textosOcr,
                                                List<String> arquivosTemp) {
        Set<Integer> consumidas = new HashSet<>();
        for (int i = 0; i < questoesDTO.size(); i++) {
            com.example.BancoDeDados.dto.QuestaoExtraidaDTO dto = questoesDTO.get(i);
            if (alternativasComTexto(dto) >= 2) continue;
            List<Integer> indices = imagensPorQuestao.get(i + 1);
            if (indices == null) continue;
            for (Integer indice : indices) {
                if (indice == null || indice >= textosOcr.size()) continue;
                List<String> alternativas = com.example.BancoDeDados.service.AlternativasOcrParser.parse(textosOcr.get(indice));
                if (alternativas == null && arquivosTemp != null && indice < arquivosTemp.size()) {
                    alternativas = ocrService.lerAlternativasComExpoentes(new File(arquivosTemp.get(indice)));
                }
                if (alternativas == null) continue;
                dto.getAlternativas().clear();
                for (int j = 0; j < alternativas.size(); j++) {
                    dto.addAlternativa(String.valueOf((char) ('a' + j)), alternativas.get(j));
                }
                consumidas.add(indice);
                System.out.println("Questão " + (i + 1) + ": alternativas lidas da imagem por OCR");
                break;
            }
        }
        return consumidas;
    }

    /** Alternativas com texto de verdade: "A) B) C)" sem conteúdo (o conteúdo é imagem) não contam. */
    private int alternativasComTexto(com.example.BancoDeDados.dto.QuestaoExtraidaDTO dto) {
        if (dto.getAlternativas() == null) return 0;
        int n = 0;
        for (com.example.BancoDeDados.dto.QuestaoExtraidaDTO.AlternativaDTO a : dto.getAlternativas()) {
            if (a.getTexto() != null && !a.getTexto().isBlank()) n++;
        }
        return n;
    }

    /**
     * Questão cujas alternativas são figuras (ex.: cinco gráficos A..E): reconhece o grupo de cinco imagens
     * do mesmo tamanho, ordena na ordem de leitura (linha de cima para baixo, da esquerda para a direita) e
     * atribui A..E. As alternativas da questão passam a ser "(figura)" e cada imagem fica ligada à sua letra
     * (QuestaoImagem.alternativaIndice). Imagens fora do grupo (ex.: a tabela do enunciado) seguem como estão.
     *
     * @return índice da imagem (em arquivosTemp) -> alternativa 0..4
     */
    private Map<Integer, Integer> figurasComoAlternativas(List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> questoesDTO,
                                                          Map<Integer, List<Integer>> imagensPorQuestao,
                                                          List<String> arquivosTemp,
                                                          List<com.example.BancoDeDados.service.PosicaoImagem> posicoes,
                                                          Set<Integer> jaConsumidas) {
        Map<Integer, Integer> resultado = new HashMap<>();
        if (posicoes == null) return resultado;
        for (int i = 0; i < questoesDTO.size(); i++) {
            com.example.BancoDeDados.dto.QuestaoExtraidaDTO dto = questoesDTO.get(i);
            if (alternativasComTexto(dto) >= 2) continue;
            List<Integer> indices = imagensPorQuestao.get(i + 1);
            if (indices == null || indices.size() < 5) continue;

            // tamanho de cada imagem candidata
            Map<Integer, int[]> tamanho = new HashMap<>();
            for (Integer idx : indices) {
                if (idx == null || idx >= arquivosTemp.size() || idx >= posicoes.size() || posicoes.get(idx) == null
                        || jaConsumidas.contains(idx)) continue;
                try {
                    java.awt.image.BufferedImage bi = javax.imageio.ImageIO.read(new File(arquivosTemp.get(idx)));
                    if (bi != null) tamanho.put(idx, new int[]{bi.getWidth(), bi.getHeight()});
                } catch (IOException ignored) {
                    // imagem ilegível: fica de fora do grupo
                }
            }

            // grupo de exatamente cinco imagens de mesmo tamanho (±12%) e na mesma página
            List<Integer> grupo = null;
            for (Integer a : tamanho.keySet()) {
                List<Integer> g = new ArrayList<>();
                for (Integer b : tamanho.keySet()) {
                    int[] ta = tamanho.get(a), tb = tamanho.get(b);
                    if (Math.abs(ta[0] - tb[0]) <= 0.12 * ta[0] && Math.abs(ta[1] - tb[1]) <= 0.12 * ta[1]
                            && posicoes.get(a).pagina() == posicoes.get(b).pagina()) g.add(b);
                }
                if (g.size() == 5) {
                    grupo = g;
                    break;
                }
            }
            if (grupo == null) continue;

            // ordem de leitura: linhas por altura (tolerância de 3% da página), dentro da linha pela esquerda
            grupo.sort(Comparator.comparingDouble((Integer k) -> posicoes.get(k).topo()));
            List<List<Integer>> linhas = new ArrayList<>();
            for (Integer k : grupo) {
                if (linhas.isEmpty() || posicoes.get(k).topo() - posicoes.get(linhas.get(linhas.size() - 1).get(0)).topo() > 0.03) {
                    linhas.add(new ArrayList<>());
                }
                linhas.get(linhas.size() - 1).add(k);
            }
            int letra = 0;
            for (List<Integer> linha : linhas) {
                linha.sort(Comparator.comparingDouble((Integer k) -> posicoes.get(k).esq()));
                for (Integer k : linha) resultado.put(k, letra++);
            }

            dto.getAlternativas().clear();
            for (int j = 0; j < 5; j++) dto.addAlternativa(String.valueOf((char) ('a' + j)), "(figura)");
            System.out.println("Questão " + (i + 1) + ": alternativas são figuras (5 imagens ligadas às letras A-E)");
        }
        return resultado;
    }

    /**
     * Converte DTO extraído para Model Questao
     */
    private List<Questao> converterDTOParaModel(List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> dtos) {
        List<Questao> questoes = new ArrayList<>();

        for (com.example.BancoDeDados.dto.QuestaoExtraidaDTO dto : dtos) {
            Questao questao = new Questao();
            questao.setCabecalho(dto.getContexto() != null ? dto.getContexto() : "");
            questao.setEnunciado(dto.getEnunciado());

            // Converter gabarito (letra para índice); null quando não informado
            if (dto.getGabarito() != null && !dto.getGabarito().isEmpty()) {
                char letra = dto.getGabarito().charAt(0);
                questao.setGabarito(letra - 'a');
            } else {
                questao.setGabarito(null);
            }

            // Adicionar alternativas
            List<String> alternativas = new ArrayList<>();
            for (com.example.BancoDeDados.dto.QuestaoExtraidaDTO.AlternativaDTO alt : dto.getAlternativas()) {
                alternativas.add(alt.getLetra() + ") " + alt.getTexto());
            }
            // Preencher até 5 alternativas se necessário
            while (alternativas.size() < 5) {
                alternativas.add("");
            }
            questao.setAlternativasTexto(alternativas);

            questoes.add(questao);
        }

        return questoes;
    }

    @PostMapping("/{listaId}/processar-pdf")
    public ResponseEntity<String> processarPdf(
            @PathVariable UUID listaId,
            @RequestParam("file") MultipartFile file) {

        try {
            // Validação básica
            if (listaId == null ) {
                return ResponseEntity.badRequest().body("ID da lista inválido");
            }

            // Verificar se a lista existe (opcional, dependendo da sua lógica de negócio)
            // listaService.verificarExistenciaLista(listaId);

            // Processar o PDF
            File tempFile = File.createTempFile("ocr_", "_" + file.getOriginalFilename());
            file.transferTo(tempFile);

            System.out.println("Recebido arquivo: " + file.getOriginalFilename());
            String textoPDF = extratorService.extrair(tempFile.getAbsolutePath());
            System.out.println("Texto extraído: " + textoPDF); // Para debug

            // Obter resposta da IA
            String respostaserviceIA = IAService.enviarParaGemini(textoPDF);
            System.out.println(IAService.getRespostaDoGemini());

            // Processar e salvar as questões
            List<Questao> questoes = tratarRespostaIA.processarRespostaIA(respostaserviceIA);

            // Salvar questões no banco e associar à lista
            listaService.salvarQuestoesComLista(questoes, listaId);

            return ResponseEntity.ok("Questões processadas e salvas com sucesso na lista ID: " + listaId);

        } catch (IOException e) {
            return ResponseEntity.status(500).body("Erro ao extrair o texto do PDF: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao processar a requisição: " + e.getMessage());
        }
    }

    @PostMapping("/processar-salvar")
    public String processarESalvarPdf() throws IOException {
        List<Questao> questoes = tratarRespostaIA.processarRespostaIA();
        questaoService.salvarQuestoes(questoes);
        return json.exibirQuestoesDoJson(json.gerarJsonRespostaIA());
    }
}
