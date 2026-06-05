package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record GrafoResponseDTO(
        List<GrafoNodeDTO> nodes,
        List<GrafoEdgeDTO> edges) {

    public record GrafoNodeDTO(
            String id,
            String nome,
            String status,
            int totalColunas,
            int totalFks) {
    }

    public record GrafoEdgeDTO(
            String source,
            String target) {
    }
}
