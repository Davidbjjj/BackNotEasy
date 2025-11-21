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

            // Converter DTO para Model e salvar
            List<Questao> questoes = converterDTOParaModel(questoesDTO);
            listaService.salvarQuestoesComLista(questoes, listaId);

            // ASSOCIAR IMAGENS ÀS QUESTÕES CORRETAS
            int totalImagensSalvas = 0;
            if (arquivosTemp != null && !arquivosTemp.isEmpty() && !questoes.isEmpty()) {
                // Agrupar imagens por questão baseado no texto OCR
                Map<Integer, List<Integer>> imagensPorQuestao =
                    imagemAssociador.agruparImagensPorQuestao(textosOcr, questoes.size());

                // Salvar apenas as imagens relevantes para cada questão
                for (int i = 0; i < questoes.size(); i++) {
                    Questao questao = questoes.get(i);
                    int numeroQuestao = i + 1; // Questões são 1-based

                    List<Integer> indicesImagens = imagensPorQuestao.get(numeroQuestao);

                    if (indicesImagens != null && !indicesImagens.isEmpty()) {
                        // Filtrar apenas as imagens desta questão
                        List<String> imagensDaQuestao = new ArrayList<>();
                        List<String> ocrsRelacionados = new ArrayList<>();

                        for (Integer indice : indicesImagens) {
                            if (indice < arquivosTemp.size()) {
                                imagensDaQuestao.add(arquivosTemp.get(indice));
                                ocrsRelacionados.add(textosOcr.get(indice));
                            }
                        }

                        if (!imagensDaQuestao.isEmpty()) {
                            List<com.example.BancoDeDados.Model.QuestaoImagem> imagens =
                                imagemQuestaoService.salvarImagensQuestao(questao, imagensDaQuestao, ocrsRelacionados);
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
     * Converte DTO extraído para Model Questao
     */
    private List<Questao> converterDTOParaModel(List<com.example.BancoDeDados.dto.QuestaoExtraidaDTO> dtos) {
        List<Questao> questoes = new ArrayList<>();

        for (com.example.BancoDeDados.dto.QuestaoExtraidaDTO dto : dtos) {
            Questao questao = new Questao();
            questao.setCabecalho(dto.getContexto() != null ? dto.getContexto() : "");
            questao.setEnunciado(dto.getEnunciado());

            // Converter gabarito (letra para índice)
            if (dto.getGabarito() != null && !dto.getGabarito().isEmpty()) {
                char letra = dto.getGabarito().charAt(0);
                questao.setGabarito(letra - 'a');
            } else {
                questao.setGabarito(-1);
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
            List<Questao> questoes = tratarRespostaIA.processarRespostaIA();

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
