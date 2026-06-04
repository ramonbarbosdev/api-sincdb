package com.api_sincdb.domain.usuario.dto;

import java.util.List;

public record MeResponseDTO(
        String idUsuario,
        String tpGlobal,
        String idOrganizacao,
        String dsRole,
        String nmUsuario,
        String nmEmail,
        List<String> permissoes) {
}
