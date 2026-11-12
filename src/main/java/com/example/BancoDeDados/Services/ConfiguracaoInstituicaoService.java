package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.ConfiguracaoInstituicao;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Repositores.ConfiguracaoInstituicaoRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Security.CallerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** FE-309: logo, cor principal, regras padrão de novas listas e integrações (ainda sem nenhuma ativa). */
@Service
public class ConfiguracaoInstituicaoService {

    private static final Pattern COR = Pattern.compile("^#[0-9a-fA-F]{6}$");
    public static final long MAX_LOGO = 1024 * 1024;

    public record Integracao(String chave, String nome, String situacao) {}

    public record Config(String corPrincipal, boolean temLogo, String logoUrl, Integer tentativasPadrao,
                         Integer tempoLimitePadraoMinutos, boolean gabaritoLiberadoPadrao, List<Integracao> integracoes) {}

    public record ConfigReq(String corPrincipal, Integer tentativasPadrao, Integer tempoLimitePadraoMinutos,
                            Boolean gabaritoLiberadoPadrao) {}

    public record Branding(String corPrincipal, String logoUrl) {}

    public record Logo(String tipo, byte[] dados) {}

    @Autowired private ConfiguracaoInstituicaoRepository repository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private ProfessorRepositores professorRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private AuditoriaService auditoria;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    /** Integrações previstas, todas inativas por enquanto (a área existe para as próximas conexões). */
    private static List<Integracao> integracoes() {
        return List.of(
                new Integracao("SSO", "Login único (SSO)", "EM_BREVE"),
                new Integracao("DIARIO", "Diário de classe / ERP escolar", "EM_BREVE"),
                new Integracao("LMS", "Plataforma de ensino (LMS)", "EM_BREVE"));
    }

    private ConfiguracaoInstituicao doBanco(UUID id) {
        return repository.findById(id).orElseGet(() -> {
            ConfiguracaoInstituicao c = new ConfiguracaoInstituicao();
            c.setInstituicaoId(id);
            return c;
        });
    }

    private static String logoUrl(ConfiguracaoInstituicao c) {
        return c.getLogoDados() == null ? null : "/branding/" + c.getInstituicaoId() + "/logo?v=" + Math.abs(java.util.Arrays.hashCode(c.getLogoDados()));
    }

    private static Config paraDto(ConfiguracaoInstituicao c) {
        return new Config(c.getCorPrincipal(), c.getLogoDados() != null, logoUrl(c), c.getTentativasPadrao(),
                c.getTempoLimitePadraoMin(), c.isGabaritoLiberadoPadrao(), integracoes());
    }

    @Transactional(readOnly = true)
    public Config obter(UUID instituicaoId) {
        return paraDto(doBanco(instituicaoId));
    }

    /** Luminância relativa (WCAG) para garantir contraste mínimo do texto branco sobre a cor principal. */
    static double contrasteComBranco(String hex) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double v = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            c[i] = v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
        }
        double l = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        return 1.05 / (l + 0.05);
    }

    @Transactional
    public Config salvar(UUID instituicaoId, ConfigReq r) {
        ConfiguracaoInstituicao c = doBanco(instituicaoId);
        String antes = resumo(c);
        if (r.corPrincipal() != null && !r.corPrincipal().isBlank()) {
            if (!COR.matcher(r.corPrincipal()).matches()) throw erro(HttpStatus.BAD_REQUEST, "Cor inválida: use o formato #RRGGBB");
            if (contrasteComBranco(r.corPrincipal()) < 4.5) {
                throw erro(HttpStatus.BAD_REQUEST, "Essa cor é clara demais: o texto branco sobre ela não atinge o contraste mínimo de 4,5:1");
            }
            c.setCorPrincipal(r.corPrincipal().toLowerCase());
        } else {
            c.setCorPrincipal(null);
        }
        if (r.tentativasPadrao() != null && (r.tentativasPadrao() < 1 || r.tentativasPadrao() > 20)) {
            throw erro(HttpStatus.BAD_REQUEST, "As tentativas padrão devem ficar entre 1 e 20");
        }
        if (r.tempoLimitePadraoMinutos() != null && (r.tempoLimitePadraoMinutos() < 1 || r.tempoLimitePadraoMinutos() > 600)) {
            throw erro(HttpStatus.BAD_REQUEST, "O tempo limite padrão deve ficar entre 1 e 600 minutos");
        }
        c.setTentativasPadrao(r.tentativasPadrao());
        c.setTempoLimitePadraoMin(r.tempoLimitePadraoMinutos());
        c.setGabaritoLiberadoPadrao(Boolean.TRUE.equals(r.gabaritoLiberadoPadrao()));
        repository.save(c);
        auditoria.registrar(instituicaoId, "CONFIG_INSTITUICAO", "Configuracao", instituicaoId, "Configurações da instituição alteradas", antes, resumo(c));
        return paraDto(c);
    }

    private static String resumo(ConfiguracaoInstituicao c) {
        return "cor=" + c.getCorPrincipal() + ", tentativas=" + c.getTentativasPadrao() + ", tempo=" + c.getTempoLimitePadraoMin()
                + ", gabarito liberado=" + c.isGabaritoLiberadoPadrao() + ", logo=" + (c.getLogoDados() != null);
    }

    @Transactional
    public Config enviarLogo(UUID instituicaoId, MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Escolha uma imagem");
        if (arquivo.getSize() > MAX_LOGO) throw erro(HttpStatus.BAD_REQUEST, "O logo pode ter no máximo 1 MB");
        byte[] dados;
        try {
            dados = arquivo.getBytes();
        } catch (IOException e) {
            throw erro(HttpStatus.BAD_REQUEST, "Não foi possível ler a imagem");
        }
        boolean png = dados.length > 8 && (dados[0] & 0xFF) == 0x89 && dados[1] == 'P' && dados[2] == 'N' && dados[3] == 'G';
        boolean jpg = dados.length > 3 && (dados[0] & 0xFF) == 0xFF && (dados[1] & 0xFF) == 0xD8;
        if (!png && !jpg) throw erro(HttpStatus.BAD_REQUEST, "Use um logo PNG ou JPG");
        ConfiguracaoInstituicao c = doBanco(instituicaoId);
        c.setLogoDados(dados);
        c.setLogoTipo(png ? "image/png" : "image/jpeg");
        repository.save(c);
        auditoria.registrar(instituicaoId, "LOGO_ALTERADO", "Configuracao", instituicaoId, "Logo da instituição atualizado", null, null);
        return paraDto(c);
    }

    @Transactional
    public Config removerLogo(UUID instituicaoId) {
        ConfiguracaoInstituicao c = doBanco(instituicaoId);
        c.setLogoDados(null);
        c.setLogoTipo(null);
        repository.save(c);
        auditoria.registrar(instituicaoId, "LOGO_REMOVIDO", "Configuracao", instituicaoId, "Logo da instituição removido", null, null);
        return paraDto(c);
    }

    @Transactional(readOnly = true)
    public Optional<Logo> logo(UUID instituicaoId) {
        return repository.findById(instituicaoId).filter(c -> c.getLogoDados() != null)
                .map(c -> new Logo(c.getLogoTipo(), c.getLogoDados()));
    }

    @Transactional(readOnly = true)
    public Branding branding(UUID instituicaoId) {
        ConfiguracaoInstituicao c = doBanco(instituicaoId);
        return new Branding(c.getCorPrincipal(), logoUrl(c));
    }

    /** Branding da instituição de quem está logado (instituição, professor ou aluno). */
    @Transactional(readOnly = true)
    public Branding minha() {
        if (!CallerContext.isAuthenticated() || CallerContext.userId() == null) {
            throw erro(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        }
        UUID uid = CallerContext.userId();
        UUID inst = null;
        if (CallerContext.instituicaoIdOuNull() != null) inst = uid;
        else if (CallerContext.isProfessor()) {
            inst = professorRepository.findById(uid).map(p -> p.getInstituicao() == null ? null : p.getInstituicao().getId()).orElse(null);
        } else if (CallerContext.isAluno()) {
            inst = estudanteRepository.findById(uid)
                    .flatMap(e -> instituicaoRepository.findByNome(e.getInstituicao())).map(Instituicao::getId).orElse(null);
        }
        return inst == null ? new Branding(null, null) : branding(inst);
    }

    /** Aplica os padrões da instituição a uma lista recém-criada (FE-309). */
    public void aplicarPadroes(Lista lista, Instituicao inst) {
        if (inst == null) return;
        repository.findById(inst.getId()).ifPresent(c -> {
            if (c.getTentativasPadrao() != null) lista.setMaxTentativas(c.getTentativasPadrao());
            if (c.getTempoLimitePadraoMin() != null) lista.setTempoLimiteMinutos(c.getTempoLimitePadraoMin());
            lista.setGabaritoLiberado(c.isGabaritoLiberadoPadrao());
        });
    }
}
