package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.AmbienteDTO;
import com.api_sincdb.domain.explorador.dto.BaseResumoDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorBaseService {

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;
    private final ExploradorMetadataCacheService cacheService;
    private final ExploradorAmbienteResolver ambienteResolver;

    public ExploradorBaseService(
            ConexaoBanco conexaoBanco,
            PostgresMetadataReader metadataReader,
            ExploradorMetadataCacheService cacheService,
            ExploradorAmbienteResolver ambienteResolver) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
        this.cacheService = cacheService;
        this.ambienteResolver = ambienteResolver;
    }

    public List<AmbienteDTO> listarAmbientes() {
        return List.of(
                new AmbienteDTO("cloud", "Cloud"),
                new AmbienteDTO("local", "Local"));
    }

    public List<BaseResumoDTO> listarBases(String token, String ambiente, String idConexao,
            boolean incluirQuantidadeSchemas) {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        String cacheKey = cacheKey(token, idConexao, ambiente, "bases|" + incluirQuantidadeSchemas);

        return cacheService.get(cacheKey, () -> unchecked(() -> {
            try (Connection conexao = conexaoBanco.abrirConexao("mudar", tipo, token, idConexao)) {
                return metadataReader.listarBases(conexao).stream()
                        .map(nome -> new BaseResumoDTO(
                                nome,
                                incluirQuantidadeSchemas ? contarSchemas(token, tipo, nome, idConexao) : null,
                                "disponivel"))
                        .toList();
            }
        }));
    }

    private long contarSchemas(String token, TipoConexao tipo, String base, String idConexao) {
        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            return metadataReader.contarSchemas(conexao);
        } catch (Exception e) {
            return 0;
        }
    }

    private String cacheKey(String token, String idConexao, String ambiente, String nivel) {
        return String.join("|",
                "explorador",
                nivel,
                String.valueOf(token == null ? 0 : token.hashCode()),
                String.valueOf(idConexao),
                ambiente.toLowerCase());
    }

    private <T> T unchecked(CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
