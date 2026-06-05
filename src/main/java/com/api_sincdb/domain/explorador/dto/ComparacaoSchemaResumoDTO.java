package com.api_sincdb.domain.explorador.dto;

public record ComparacaoSchemaResumoDTO(
        long totalTabelas,
        long tabelasIguais,
        long tabelasDiferentes,
        long ausentesDestino,
        long novasDestino) {
}
