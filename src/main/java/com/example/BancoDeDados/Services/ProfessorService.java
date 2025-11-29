package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.ResponseDTO.ProfessorDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfessorService {

    @Autowired
    private ProfessorRepositores professorRepositores;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private InstituicaoRepository instituicaoRepository;

    @Autowired
    private DisciplinaRepository disciplinaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Transactional
    public Professor criar(Professor professor) {
        if (!validarSenha(professor.getSenha())) {
            throw new IllegalArgumentException(
                    "A senha não atende aos requisitos: mínimo de 8 caracteres, incluindo letras maiúsculas, minúsculas e números.");
        } else if (!validarEmail(professor.getEmail())) {
            throw new IllegalArgumentException(
                    "Email inválido.");
        }

        return professorRepositores.save(professor);
    }

    // Novo método que centraliza criação e validações para reduzir round-trips
    @Transactional
    public Professor registerProfessor(ProfessorResponseDTO dto) {
        // Verificar existência de email com consulta leve
        if (accountRepository.findByEmail(dto.email()).isPresent()) {
            throw new IllegalArgumentException("Email já cadastrado");
        }

        // Buscar instituicao uma vez
        Instituicao instituicao = instituicaoRepository.findById(dto.instituicaoId())
                .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

        // Verificar se disciplinas existem (buscar por ids)
        UUID mat1 = dto.materia1Id();
        UUID mat2 = dto.materia2Id();
        Disciplina disciplina1 = disciplinaRepository.findById(mat1)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina 1 não encontrada"));
        Disciplina disciplina2 = disciplinaRepository.findById(mat2)
                .orElseThrow(() -> new IllegalArgumentException("Disciplina 2 não encontrada"));

        // Criar professor e salvar (senha será salva por Account, mas mantemos campo senha no professor também)
        Professor professor = new Professor(dto, instituicao);
        professor.setSenha(passwordEncoder.encode(dto.senha()));
        Professor savedProfessor = professorRepositores.save(professor);

        // Atualizar relações das disciplinas para apontar para o novo professor (se necessário)
        disciplina1.setProfessor(savedProfessor);
        disciplina2.setProfessor(savedProfessor);
        disciplinaRepository.save(disciplina1);
        disciplinaRepository.save(disciplina2);

        // Criar e salvar Account vinculado
        Account account = new Account();
        account.setEmail(dto.email());
        account.setSenha(passwordEncoder.encode(dto.senha()));
        account.setRole(com.example.BancoDeDados.Model.Role.PROFESSOR);
        account.setProfessorProfile(savedProfessor);
        accountRepository.save(account);

        return savedProfessor;
    }

    public List<ProfessorDTO> listar() {
        List<Professor> professores = professorRepositores.findAll();
        return professores.stream()
                .map(ProfessorDTO::new)
                .collect(Collectors.toList());
    }

    public boolean deletar(UUID id) {
        try {
            if (professorRepositores.existsById(id)) {
                professorRepositores.deleteById(id);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar o professor: " + e.getMessage());
        }
    }

    public Optional<Professor> editar(UUID id) {
        try {
            return professorRepositores.findById(id);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar o professor: " + e.getMessage());
        }
    }

    public boolean validarSenha(String senha) {
        if (senha.length() < 8) {
            return false;
        }
        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;

        for (char c : senha.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpperCase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowerCase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasUpperCase && hasLowerCase && hasDigit) {
                return true;
            }
        }
        return false;
    }

    public boolean validarEmail(String email) {
        if (email == null) {
            return false;
        }

        int atIndex = email.indexOf('@');
        int dotIndex = email.lastIndexOf('.');

        return atIndex > 0 && dotIndex > atIndex + 1 && dotIndex < email.length() - 1;
    }

}