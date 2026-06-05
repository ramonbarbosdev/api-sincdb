package com.api_sincdb.domain.explorador.dto;

import java.util.List;
import java.util.Map;

public record DadosTabelaDTO(
        String ambiente,
        String base,
        String schema,
        String tabela,
        int limit,
        List<String> colunas,
        List<Map<String, Object>> registros) {
}
