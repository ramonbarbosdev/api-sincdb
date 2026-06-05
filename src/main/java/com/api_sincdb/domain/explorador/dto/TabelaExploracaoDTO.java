package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record TabelaExploracaoDTO(
        String ambiente,
        String base,
        String id,
        String schema,
        String nome,
        int totalColunas,
        int totalIndices,
        int totalFks,
        List<ColunaExploracaoDTO> colunas,
        List<IndiceExploracaoDTO> indices,
        List<ForeignKeyExploracaoDTO> foreignKeys,
        List<AcaoTabelaDTO> acoesDisponiveis,
        String sqlPreview) {

    public record ColunaExploracaoDTO(
            String nome,
            String tipo,
            Integer tamanho,
            boolean nullable,
            boolean primaryKey) {
    }

    public record IndiceExploracaoDTO(
            String nome,
            List<String> colunas,
            boolean unico) {
    }

    public record ForeignKeyExploracaoDTO(
            String nome,
            String coluna,
            String tabelaReferencia,
            String colunaReferencia) {
    }

    public record AcaoTabelaDTO(
            String id,
            String label,
            boolean disponivel) {
    }
}
