package com.api_sincdb.domain.sql.dto;

import java.util.List;
import java.util.Map;

public class SqlExecutionResponse {

    private List<SqlColumnDTO> columns;
    private List<Map<String, Object>> rows;
    private long executionTimeMs;
    private int affectedRows;
    private String message;
    private boolean requiresConfirmation;
    private boolean requiresParameters;
    private List<String> parameters;
    private String riskLevel;

    public SqlExecutionResponse() {
    }

    public SqlExecutionResponse(List<SqlColumnDTO> columns, List<Map<String, Object>> rows,
            long executionTimeMs, int affectedRows, String message, boolean requiresConfirmation, String riskLevel) {
        this.columns = columns;
        this.rows = rows;
        this.executionTimeMs = executionTimeMs;
        this.affectedRows = affectedRows;
        this.message = message;
        this.requiresConfirmation = requiresConfirmation;
        this.riskLevel = riskLevel;
    }

    public SqlExecutionResponse(List<SqlColumnDTO> columns, List<Map<String, Object>> rows,
            long executionTimeMs, int affectedRows, String message, boolean requiresConfirmation,
            boolean requiresParameters, List<String> parameters, String riskLevel) {
        this.columns = columns;
        this.rows = rows;
        this.executionTimeMs = executionTimeMs;
        this.affectedRows = affectedRows;
        this.message = message;
        this.requiresConfirmation = requiresConfirmation;
        this.requiresParameters = requiresParameters;
        this.parameters = parameters;
        this.riskLevel = riskLevel;
    }

    public List<SqlColumnDTO> getColumns() {
        return columns;
    }

    public void setColumns(List<SqlColumnDTO> columns) {
        this.columns = columns;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public int getAffectedRows() {
        return affectedRows;
    }

    public void setAffectedRows(int affectedRows) {
        this.affectedRows = affectedRows;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public boolean isRequiresParameters() {
        return requiresParameters;
    }

    public void setRequiresParameters(boolean requiresParameters) {
        this.requiresParameters = requiresParameters;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public void setParameters(List<String> parameters) {
        this.parameters = parameters;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }
}
