package com.api_sincdb.domain.explorador.dto;

public record DiagramNodeDTO(
        String id,
        String schema,
        String nome,
        String status,
        int totalColunas,
        long totalDiferencas,
        int totalFks) {
}
