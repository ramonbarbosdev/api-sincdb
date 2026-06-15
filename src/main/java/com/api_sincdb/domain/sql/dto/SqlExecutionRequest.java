package com.api_sincdb.domain.sql.dto;

import java.util.Map;

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

    private Integer maxRows;
    private Integer timeoutSeconds;
    private Boolean confirmado;
    private Map<String, Object> parametros;

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

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getConfirmado() {
        return confirmado;
    }

    public void setConfirmado(Boolean confirmado) {
        this.confirmado = confirmado;
    }

    public Map<String, Object> getParametros() {
        return parametros;
    }

    public void setParametros(Map<String, Object> parametros) {
        this.parametros = parametros;
    }
}
