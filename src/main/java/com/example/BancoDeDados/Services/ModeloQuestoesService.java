package com.example.BancoDeDados.Services;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente do serviço Python (ml-service) que executa o modelo.joblib.
 * Substitui a chamada à API de IA na extração de questões.
 */
@Service
public class ModeloQuestoesService {

    private static final int MAX_TENTATIVAS = 5;

    private static final Logger log =LoggerFactory.getLogger(ModeloQuestoesService.class);

    @Value("${modelo.url:http://localhost:8000}")
    private String modeloUrl;

    private final RestTemplate restTemplate;

    public ModeloQuestoesService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(180_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Extrai todas as questões de um documento.
     * Cada item: {is_question, probabilidade, cabecalho, enunciado, alternativas[], gabarito (índice ou null)}.
     */
    public JSONArray extrairQuestoes(String texto) throws IOException {
        JSONArray questoes = new JSONArray();
        if (texto == null || texto.isBlank()) return questoes;

        // Segmentação local só para ler as linhas de gabarito; o documento inteiro
        // (sem essas linhas) vai ao serviço do modelo em UMA única requisição.
        List<String> semGabarito = new ArrayList<>();
        List<String> letras = new ArrayList<>();
        for (String bloco : segmentar(texto)) {
            String[] separado = separarGabarito(bloco);
            semGabarito.add(separado[0]);
            letras.add(separado[1]);
        }
        if (semGabarito.isEmpty()) return questoes;

        // O serviço pode devolver um objeto (uma questão) ou um array (várias questões)
        Object resposta = post("/analisar", String.join("\n\n", semGabarito));
        JSONArray analises = resposta instanceof JSONArray ? (JSONArray) resposta
                : new JSONArray().put(resposta);

        // O gabarito só é associado por posição quando o modelo devolveu uma análise por bloco
        boolean mapeavel = analises.length() == letras.size();
        for (int i = 0; i < analises.length(); i++) {
            JSONObject analise = analises.optJSONObject(i);
            if (analise == null || !analise.optBoolean("is_question", false)) continue;

            JSONArray alternativas = analise.optJSONArray("alternativas");
            Object gabarito = JSONObject.NULL;
            String letra = mapeavel ? letras.get(i) : null;
            if (letra != null && alternativas != null) {
                int idx = letra.charAt(0) - 'a';
                if (idx >= 0 && idx < alternativas.length()) gabarito = idx;
            }
            analise.put("gabarito", gabarito);
            questoes.put(analise);
        }
        return questoes;
    }

    // ---------- Segmentação do documento em blocos de questão ----------

    private static final int TAMANHO_MIN_BLOCO = 20;
    private static final int FLAGS = Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    /** "12." / "12)" / "Questão 12" no início da linha. */
    private static final Pattern MARCA_NUMERO = Pattern.compile(
            "^[ \\t]*(?:quest[ãa]o[ \\t]*(\\d{1,3})\\b[ \\t]*[.)\\-–:]?|(\\d{1,3})[ \\t]*[.)\\-–:][ \\t]+(?=\\S))", FLAGS);
    /** a) b) c)... */
    private static final Pattern ALT = Pattern.compile("(?:^|\\s)\\(?([a-eA-E])[).]\\s+");
    private static final Pattern GABARITO = Pattern.compile(
            "^[ \\t]*(?:gabarito|resposta|resp\\.?)[ \\t]*[:\\-]?[ \\t]*\\(?([a-eA-E])\\)?[ \\t]*\\.?[ \\t]*$", FLAGS);
    private static final Pattern LINHA_EM_BRANCO = Pattern.compile("\\n[ \\t]*\\n");

    static List<String> segmentar(String texto) {
        List<String> blocos = segmentarPorNumero(texto);
        if (blocos == null) blocos = segmentarPorAlternativas(texto);
        List<String> saida = new ArrayList<>();
        for (String b : blocos) {
            String t = b.strip();
            if (t.length() >= TAMANHO_MIN_BLOCO) saida.add(t);
        }
        return saida;
    }

    /** Divide nos marcadores de questão cuja numeração é crescente. Null se houver menos de 2. */
    private static List<String> segmentarPorNumero(String texto) {
        List<Integer> inicios = new ArrayList<>();
        Integer ultimo = null;
        Matcher m = MARCA_NUMERO.matcher(texto);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(2));
            if (ultimo == null || (n > ultimo && n <= ultimo + 2)) {
                inicios.add(m.start());
                ultimo = n;
            }
        }
        if (inicios.size() < 2) return null;
        List<String> blocos = new ArrayList<>();
        blocos.add(texto.substring(0, inicios.get(0))); // eventual questão sem número antes do 1º marcador
        for (int i = 0; i < inicios.size(); i++) {
            int fim = i + 1 < inicios.size() ? inicios.get(i + 1) : texto.length();
            blocos.add(texto.substring(inicios.get(i), fim));
        }
        return blocos;
    }

    /** Sem numeração: corta ao fim de cada sequência a), b), c)... */
    private static List<String> segmentarPorAlternativas(String texto) {
        List<Integer> fimDasSequencias = new ArrayList<>();
        int ultimoFim = -1, tamanho = 0;
        char esperado = 'a';
        Matcher m = ALT.matcher(texto);
        while (m.find()) {
            char letra = Character.toLowerCase(m.group(1).charAt(0));
            if (letra == 'a') {
                if (tamanho >= 2) fimDasSequencias.add(ultimoFim);
                tamanho = 1;
                esperado = 'b';
                ultimoFim = m.end();
            } else if (tamanho > 0 && letra == esperado) {
                tamanho++;
                esperado++;
                ultimoFim = m.end();
            }
        }
        if (tamanho >= 2) fimDasSequencias.add(ultimoFim);

        List<String> blocos = new ArrayList<>();
        int inicio = 0;
        for (int fimAlt : fimDasSequencias) {
            Matcher corte = LINHA_EM_BRANCO.matcher(texto);
            int fim = corte.find(fimAlt) ? corte.start() : texto.length();
            blocos.add(texto.substring(inicio, fim));
            inicio = fim;
        }
        if (blocos.isEmpty()) blocos.add(texto);
        return blocos;
    }

    /** Remove uma linha explícita de gabarito ("Gabarito: C") e devolve {bloco, letra|null}. */
    private static String[] separarGabarito(String bloco) {
        Matcher m = GABARITO.matcher(bloco);
        if (!m.find()) return new String[]{bloco, null};
        String semLinha = (bloco.substring(0, m.start()) + bloco.substring(m.end())).strip();
        return new String[]{semLinha, m.group(1).toLowerCase()};
    }

    /** Classifica e estrutura um único texto (uma questão). */
    public JSONObject analisar(String texto) throws IOException {
        Object resposta = post("/analisar", texto == null ? "" : texto);
        if (resposta instanceof JSONArray) {
            JSONObject primeiro = ((JSONArray) resposta).optJSONObject(0);
            return primeiro != null ? primeiro : new JSONObject().put("is_question", false);
        }
        return (JSONObject) resposta;
    }

    /** Devolve um JSONObject ou um JSONArray, conforme o corpo da resposta. */
    private Object post(String caminho, String texto) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
        String corpo = new JSONObject().put("texto", texto).toString();
        Exception ultimo = null;
        // O modelo leva alguns segundos para carregar quando o serviço acabou de iniciar
        for (int tentativa = 0; tentativa < MAX_TENTATIVAS; tentativa++) {
            try {
                log.info("[modelo] REQUEST enviado para {}{}: {}", modeloUrl, caminho, corpo);
                ResponseEntity<String> response = restTemplate.exchange(
                        modeloUrl + caminho, HttpMethod.POST, new HttpEntity<>(corpo, headers), String.class);
                String body = response.getBody() == null ? "" : response.getBody().strip();
                log.info("[modelo] RESPONSE de {}{} (status {}): {}", modeloUrl, caminho, response.getStatusCode(), body);
                return body.startsWith("[") ? new JSONArray(body) : new JSONObject(body);
            } catch (org.springframework.web.client.ResourceAccessException e) {
                ultimo = e;
                log.warn("[modelo] tentativa {}/{} falhou: {}", tentativa + 1, MAX_TENTATIVAS, e.getMessage());
                // Timeout de leitura = o serviço está de pé mas lento; repetir só empilharia requisições
                if (e.getMessage() != null && e.getMessage().contains("Read timed out")) break;
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                ultimo = e;
                break;
            }
        }
        throw new IOException("Falha ao consultar o serviço do modelo em " + modeloUrl + caminho + ": "
                + (ultimo != null ? ultimo.getMessage() : "sem resposta"), ultimo);
    }
}
