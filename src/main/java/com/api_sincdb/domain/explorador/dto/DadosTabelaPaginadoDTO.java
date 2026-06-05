package com.api_sincdb.domain.explorador.dto;

import java.util.List;
import java.util.Map;

public record DadosTabelaPaginadoDTO(
        String ambiente,
        String base,
        String schema,
        String tabela,
        int page,
        int size,
        List<String> colunas,
        List<Map<String, Object>> registros) {
}
