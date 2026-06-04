package com.api_sincdb.domain.explorador.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExploradorVisualResponseDTO(
        String base,
        String esquema,
        LocalDateTime geradoEm,
        AmbienteDTO origem,
        AmbienteDTO destino,
        ComparacaoDTO comparacao) {

    public record AmbienteDTO(
            String nome,
            String tipo,
            String status,
            List<SchemaDTO> schemas) {
    }

    public record SchemaDTO(
            String nome,
            List<TabelaDTO> tabelas) {
    }

    public record TabelaDTO(
            String schema,
            String nome,
            String nomeCompleto,
            String status,
            List<ColunaDTO> colunas,
            List<IndiceDTO> indices,
            List<ForeignKeyDTO> foreignKeys) {
    }

    public record ColunaDTO(
            String nome,
            String tipo,
            Integer tamanho,
            boolean nullable,
            boolean primaryKey,
            String status) {
    }

    public record IndiceDTO(
            String nome,
            List<String> colunas,
            boolean unico,
            String status) {
    }

    public record ForeignKeyDTO(
            String nome,
            String coluna,
            String tabelaReferencia,
            String colunaReferencia,
            String status) {
    }

    public record ComparacaoDTO(
            List<TabelaComparacaoDTO> tabelas,
            ResumoDTO resumo,
            List<String> sqlPreview) {
    }

    public record TabelaComparacaoDTO(
            String schema,
            String nome,
            String nomeCompleto,
            String status,
            List<ColunaComparacaoDTO> colunas,
            List<IndiceDTO> indices,
            List<ForeignKeyDTO> foreignKeys,
            List<String> observacoes) {
    }

    public record ColunaComparacaoDTO(
            String nome,
            String tipoOrigem,
            String tipoDestino,
            boolean primaryKeyOrigem,
            boolean primaryKeyDestino,
            String status,
            String observacao) {
    }

    public record ResumoDTO(
            long tabelasIguais,
            long tabelasDiferentes,
            long tabelasAusentesDestino,
            long tabelasNovasDestino,
            long colunasIguais,
            long colunasDiferentes,
            long colunasAusentesDestino,
            long colunasNovasDestino,
            long indicesDiferentes,
            long foreignKeysDiferentes) {
    }
}
