package com.api_sincdb.domain.explorador.dto;

import java.util.List;

public record TabelaDetalheDTO(
        String id,
        String schema,
        String nome,
        String status,
        List<ColunaDetalheDTO> colunas,
        List<IndiceDetalheDTO> indices,
        List<ForeignKeyDetalheDTO> foreignKeys,
        List<String> observacoes,
        String sqlPreview) {

    public record ColunaDetalheDTO(
            String nome,
            String tipoOrigem,
            String tipoDestino,
            boolean primaryKeyOrigem,
            boolean primaryKeyDestino,
            String status,
            String observacao) {
    }

    public record IndiceDetalheDTO(
            String nome,
            List<String> colunas,
            boolean unico,
            String status) {
    }

    public record ForeignKeyDetalheDTO(
            String nome,
            String coluna,
            String tabelaReferencia,
            String colunaReferencia,
            String status) {
    }
}
