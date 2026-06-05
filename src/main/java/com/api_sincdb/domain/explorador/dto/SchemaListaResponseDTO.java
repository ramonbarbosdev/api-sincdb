package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record SchemaListaResponseDTO(
        List<SchemaItemDTO> schemas) {

    public record SchemaItemDTO(
            String nome,
            long totalTabelas) {
    }
}
