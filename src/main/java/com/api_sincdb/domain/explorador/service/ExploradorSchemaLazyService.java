package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.SchemaListaResponseDTO;
import com.api_sincdb.domain.explorador.dto.SchemaListaResponseDTO.SchemaItemDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorSchemaLazyService {

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;
    private final ExploradorMetadataCacheService cacheService;
    private final ExploradorAmbienteResolver ambienteResolver;

    public ExploradorSchemaLazyService(
            ConexaoBanco conexaoBanco,
            PostgresMetadataReader metadataReader,
            ExploradorMetadataCacheService cacheService,
            ExploradorAmbienteResolver ambienteResolver) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
        this.cacheService = cacheService;
        this.ambienteResolver = ambienteResolver;
    }

    public SchemaListaResponseDTO listarSchemas(String token, String ambiente, String base, String idConexao) {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        String cacheKey = cacheKey(token, idConexao, ambiente, base);

        return cacheService.get(cacheKey, () -> unchecked(() -> {
            try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
                return new SchemaListaResponseDTO(metadataReader.listarSchemasResumo(conexao).stream()
                        .map(schema -> new SchemaItemDTO(schema.nome(), schema.totalTabelas()))
                        .toList());
            }
        }));
    }

    private String cacheKey(String token, String idConexao, String ambiente, String base) {
        return String.join("|", "explorador", "schemas", String.valueOf(token == null ? 0 : token.hashCode()),
                String.valueOf(idConexao), ambiente.toLowerCase(), base);
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
