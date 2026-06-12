package com.api_sincdb.domain.sql.dto;

public record SavedSqlQueryDTO(
        String id,
        String nome,
        String ambiente,
        String conexaoId,
        String base,
        String sql) {
}
