package com.api_sincdb.domain.conexao.dto;

import com.api_sincdb.domain.conexao.model.Conexao;

public record ConexaoResponseDTO(
    String id,
    String nm_conexao,
    String db_cloud_host,
    String db_cloud_port,
    String db_cloud_user,
    Boolean fl_padrao,
    Boolean fl_ativo
) {
    public static ConexaoResponseDTO fromEntity(Conexao conexao) {
        return new ConexaoResponseDTO(
            conexao.getId(),
            conexao.getNm_conexao(),
            conexao.getDb_cloud_host(),
            conexao.getDb_cloud_port(),
            conexao.getDb_cloud_user(),
            conexao.getFl_padrao(),
            conexao.getFl_ativo()
        );
    }
}