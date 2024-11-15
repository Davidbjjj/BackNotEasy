package com.example.BancoDeDados.Services;

import java.util.List;
import java.lang.Integer;
import java.util.Optional;

import org.antlr.v4.runtime.misc.IntegerList;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap.Option;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Usuario;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;

@Service
public class ServiceEstudante {
    @Autowired
    private EstudanteRepositores estudanteRepositores;

    public Estudante criar(Estudante estudante) {
        if (!validarSenha(estudante.getSenha())) {
            throw new IllegalArgumentException(
                    "A senha não atende aos requisitos: mínimo de 8 caracteres, incluindo letras maiúsculas, minúsculas e números.");
        } else if (!validarEmail(estudante.getEmail())) {
            throw new IllegalArgumentException(
                    "Email inválido.");
        }
        return estudanteRepositores.save(estudante);
    }

    public List<Estudante> listaEstudantes(Estudante estudante) {

        return estudanteRepositores.findAll();

    }

    public boolean deletar(Integer id) {
        try {
            if (estudanteRepositores.existsById(id)) {
                estudanteRepositores.deleteById(id);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar o Estudante: " + e.getMessage());
        }

    }

    public Optional<Estudante> editar(Integer id) {
        try {
            return estudanteRepositores.findById(id);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar o usuário: " + e.getMessage());
        }

    }

    private boolean validarSenha(String senha) {
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

        if (atIndex > 0 && dotIndex > atIndex + 1 && dotIndex < email.length() - 1) {
            return true;
        } else {
            return false;
        }
    }
}
