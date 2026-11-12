package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.ConfiguracaoInstituicao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ConfiguracaoInstituicaoRepository extends JpaRepository<ConfiguracaoInstituicao, UUID> {
}
