package com.api_sincdb.domain.explorador.dto;

import java.util.List;

import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.ColunaDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.ForeignKeyDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.IndiceDetalheDTO;

public record TabelaComparacaoDetalheDTO(
        String status,
        List<ColunaDetalheDTO> colunas,
        List<IndiceDetalheDTO> indices,
        List<ForeignKeyDetalheDTO> foreignKeys,
        List<String> observacoes,
        String sqlPreview) {
}
