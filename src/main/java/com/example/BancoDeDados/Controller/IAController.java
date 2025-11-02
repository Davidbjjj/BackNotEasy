package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

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
