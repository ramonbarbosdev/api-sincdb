package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.GrafoResponseDTO;
import com.api_sincdb.domain.explorador.dto.GrafoResponseDTO.GrafoEdgeDTO;
import com.api_sincdb.domain.explorador.dto.GrafoResponseDTO.GrafoNodeDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorGrafoService {

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;
    private final ExploradorMetadataCacheService cacheService;
    private final ExploradorAmbienteResolver ambienteResolver;

    public ExploradorGrafoService(
            ConexaoBanco conexaoBanco,
            PostgresMetadataReader metadataReader,
            ExploradorMetadataCacheService cacheService,
            ExploradorAmbienteResolver ambienteResolver) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
        this.cacheService = cacheService;
        this.ambienteResolver = ambienteResolver;
    }

    public GrafoResponseDTO carregarGrafo(String token, String ambiente, String base, String schema,
            String idConexao) {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        String cacheKey = String.join("|", "explorador", "grafo", String.valueOf(token == null ? 0 : token.hashCode()),
                String.valueOf(idConexao), ambiente.toLowerCase(), base, schema);

        return cacheService.get(cacheKey, () -> unchecked(() -> {
            try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
                var nodes = metadataReader.listarTabelasGrafo(conexao, schema).stream()
                        .map(tabela -> new GrafoNodeDTO(tabela.id(), tabela.nome(), "desconhecido",
                                tabela.totalColunas(), tabela.totalFks()))
                        .toList();

                var nodeIds = nodes.stream()
                        .map(GrafoNodeDTO::id)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

                var edges = metadataReader.listarForeignKeysGrafo(conexao, schema).stream()
                        .filter(fk -> nodeIds.contains(fk.source()) && nodeIds.contains(fk.target()))
                        .map(fk -> new GrafoEdgeDTO(fk.source(), fk.target()))
                        .toList();

                return new GrafoResponseDTO(nodes, edges);
            }
        }));
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
