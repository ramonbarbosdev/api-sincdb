package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record DiagramResponseDTO(
        String base,
        String schema,
        List<DiagramNodeDTO> nodes,
        List<DiagramEdgeDTO> edges,
        ResumoComparacaoDTO resumo) {
}
