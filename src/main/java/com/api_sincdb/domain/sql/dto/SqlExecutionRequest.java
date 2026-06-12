package com.api_sincdb.domain.sql.dto;

import jakarta.validation.constraints.NotBlank;

public class SqlExecutionRequest {

    @NotBlank(message = "Ambiente nao informado.")
    private String ambiente;

    @NotBlank(message = "Conexao nao informada.")
    private String conexaoId;

    @NotBlank(message = "Base nao informada.")
    private String base;

    @NotBlank(message = "SQL nao informado.")
    private String sql;

    public String getAmbiente() {
        return ambiente;
    }

    public void setAmbiente(String ambiente) {
        this.ambiente = ambiente;
    }

    public String getConexaoId() {
        return conexaoId;
    }

    public void setConexaoId(String conexaoId) {
        this.conexaoId = conexaoId;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }
}
