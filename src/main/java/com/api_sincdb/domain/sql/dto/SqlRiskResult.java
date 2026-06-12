package com.api_sincdb.domain.sql.dto;

public class SqlRiskResult {

    private String sql;
    private String riskLevel;
    private boolean requiresConfirmation;
    private String command;

    public SqlRiskResult() {
    }

    public SqlRiskResult(String sql, String riskLevel, boolean requiresConfirmation, String command) {
        this.sql = sql;
        this.riskLevel = riskLevel;
        this.requiresConfirmation = requiresConfirmation;
        this.command = command;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
