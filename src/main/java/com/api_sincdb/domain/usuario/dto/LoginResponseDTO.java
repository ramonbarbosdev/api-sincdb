package com.api_sincdb.domain.usuario.dto;

import java.util.List;

public record LoginResponseDTO(
        String accessToken,
        String tpGlobal,
        boolean precisaSelecionarOrganizacao,
        boolean trocarSenha,
        List<OrganizacaoLoginDTO> organizacoes) {
}
