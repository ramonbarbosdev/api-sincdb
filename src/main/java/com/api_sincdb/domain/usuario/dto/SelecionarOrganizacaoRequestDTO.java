package com.api_sincdb.domain.usuario.dto;

import jakarta.validation.constraints.NotBlank;

public record SelecionarOrganizacaoRequestDTO(@NotBlank String idOrganizacao) {
}
