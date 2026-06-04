package com.api_sincdb.domain.usuario.dto;

import java.util.List;

public record SelecionarOrganizacaoResponseDTO(
        String accessToken,
        String idOrganizacao,
        String dsRole,
        List<String> permissoes) {
}
