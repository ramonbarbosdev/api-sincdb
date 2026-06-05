package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record TabelaListaResponseDTO(
        List<TabelaItemDTO> tabelas) {

    public record TabelaItemDTO(
            String nome,
            int totalColunas,
            long estimativaRegistros) {
    }
}
