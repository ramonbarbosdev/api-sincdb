package com.api_sincdb.domain.explorador.dto;

public record BaseResumoDTO(
        String nome,
        Long quantidadeSchemas,
        String status) {
}
