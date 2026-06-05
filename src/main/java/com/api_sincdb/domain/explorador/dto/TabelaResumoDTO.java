package com.api_sincdb.domain.explorador.dto;

public record TabelaResumoDTO(
        String id,
        String schema,
        String nome,
        int totalColunas,
        int totalIndices,
        int totalFks) {
}
