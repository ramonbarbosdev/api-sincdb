package com.api_sincdb.domain.explorador.dto;

public record ResumoComparacaoDTO(
        long totalTabelas,
        long tabelasIguais,
        long tabelasDiferentes,
        long ausentesDestino,
        long novasDestino,
        long colunasDiferentes) {
}
