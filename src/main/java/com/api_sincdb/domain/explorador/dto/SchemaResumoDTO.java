package com.api_sincdb.domain.explorador.dto;

public record SchemaResumoDTO(
        String schema,
        long totalTabelas,
        long tabelasIguais,
        long tabelasDiferentes,
        long ausentesDestino,
        long novasDestino,
        long colunasDiferentes,
        String status) {
}
