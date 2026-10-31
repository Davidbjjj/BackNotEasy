package com.example.BancoDeDados.Services;


import com.example.BancoDeDados.ResponseDTO.InstituicaoRequest;
import com.example.BancoDeDados.ResponseDTO.InstituicaoResponse;
import com.example.BancoDeDados.Exceptions.EmailJaCadastradoException;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class InstituicaoService {

    private final InstituicaoRepository instituicaoRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public InstituicaoService(InstituicaoRepository instituicaoRepository) {
        this.instituicaoRepository = instituicaoRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public InstituicaoResponse cadastrarInstituicao(InstituicaoRequest request) {
        if (instituicaoRepository.existsByEmail(request.getEmail())) {
            throw new EmailJaCadastradoException("E-mail já está em uso!");
        }

        Instituicao instituicao = new Instituicao();
        instituicao.setNome(request.getNome());
        instituicao.setEmail(request.getEmail());
        instituicao.setSenha(passwordEncoder.encode(request.getSenha()));
        instituicao.setEndereco(request.getEndereco());

        Instituicao saved = instituicaoRepository.save(instituicao);

        return new InstituicaoResponse(
                saved.getId(),
                saved.getNome(),
                saved.getEmail(),
                saved.getEndereco(),
                saved.getRole(),
                saved.getEmailsPermitidos()
        );
    }

    public List<Instituicao> listar() {
        return instituicaoRepository.findAll();
    }

    public boolean deletar(UUID id) {
        try {
            if (instituicaoRepository.existsById(id)) {
                instituicaoRepository.deleteById(id);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar o usuário: " + e.getMessage());
        }
    }

    public Optional<Instituicao> editar(UUID id) {
        try {
            return instituicaoRepository.findById(id);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar o usuário: " + e.getMessage());
        }
    }
}
