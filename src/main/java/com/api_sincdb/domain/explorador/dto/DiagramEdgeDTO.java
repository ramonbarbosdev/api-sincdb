package com.api_sincdb.domain.explorador.dto;

public record DiagramEdgeDTO(
        String id,
        String source,
        String target,
        String status,
        String label) {
}
