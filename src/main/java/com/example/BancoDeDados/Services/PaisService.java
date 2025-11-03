package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Pais;
import com.example.BancoDeDados.Repositores.PaisRepositores;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PaisService {

    @Autowired
    private PaisRepositores paisRepositories;

    @Transactional
    public Pais createPais(Pais pais) {
        try {
            return paisRepositories.save(pais);
        } catch(Exception e) {
            throw new RuntimeException("Erro ao criar pais" + e.getMessage());
        }
    }

    public List<Pais> listarPais(Pais pais) {
        return paisRepositories.findAll();
    }

    public boolean deletar(Integer id) {
        try {
            if (paisRepositories.existsById(id)) {
                paisRepositories.deleteById(id);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao deletar o usuário: " + e.getMessage());
        }
    }
    public Optional<Pais> editar(Integer id) {
        try {
            return paisRepositories.findById(id);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao buscar o usuário: " + e.getMessage());
        }
    }
}
