package com.api_sincdb.domain.sql.dto;

import java.time.LocalDateTime;

public record SqlHistoryDTO(
        String id,
        String idUsuario,
        String id_empresa,
        String id_tenant,
        String ambiente,
        String conexaoId,
        String base,
        String sql,
        long executionTimeMs,
        boolean success,
        String errorMessage,
        LocalDateTime executedAt) {
}
