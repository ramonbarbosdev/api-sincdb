package com.api_sincdb.domain.sql.service;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.domain.sql.dto.SqlRiskResult;

@Service
public class SqlRiskAnalyzer {

    public static final int MAX_SQL_LENGTH = 20000;

    private static final Pattern DANGEROUS_COMMANDS = Pattern.compile(
            "\\b(insert|update|delete|alter|create|drop|truncate)\\b",
            Pattern.CASE_INSENSITIVE);

    public SqlRiskResult analyze(String sql) {
        if (sql == null || sql.isBlank()) {
            throw badRequest("SQL nao pode ser vazio.");
        }

        if (sql.length() > MAX_SQL_LENGTH) {
            throw badRequest("SQL excede o tamanho maximo permitido.");
        }

        String sqlSemComentarios = removerComentarios(sql).trim();
        String sqlParaAnalise = mascararStrings(sqlSemComentarios).trim();

        if (sqlParaAnalise.isBlank()) {
            throw badRequest("SQL nao pode ser vazio.");
        }

        if (temMultiplasInstrucoes(sqlParaAnalise) && DANGEROUS_COMMANDS.matcher(sqlParaAnalise).find()) {
            throw badRequest("Multiplas instrucoes perigosas nao sao permitidas sem modo script.");
        }

        String comando = primeiroComando(sqlParaAnalise);
        String riskLevel = classificarRisco(comando, sqlParaAnalise);

        return new SqlRiskResult(
                removerPontoEVirgulaFinal(sqlSemComentarios),
                riskLevel,
                exigeConfirmacao(riskLevel),
                comando);
    }

    private String classificarRisco(String comando, String sql) {
        return switch (comando) {
            case "select", "explain" -> "LOW";
            case "insert" -> "MEDIUM";
            case "update" -> contemWhere(sql) ? "MEDIUM" : "HIGH";
            case "delete" -> contemWhere(sql) ? "MEDIUM" : "HIGH";
            case "alter", "create" -> "HIGH";
            case "drop", "truncate" -> "CRITICAL";
            default -> "HIGH";
        };
    }

    private boolean exigeConfirmacao(String riskLevel) {
        return "HIGH".equals(riskLevel) || "CRITICAL".equals(riskLevel);
    }

    private boolean contemWhere(String sql) {
        return Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE).matcher(sql).find();
    }

    private String primeiroComando(String sql) {
        String[] partes = sql.trim().split("\\s+", 2);
        return partes[0].toLowerCase(Locale.ROOT);
    }

    private boolean temMultiplasInstrucoes(String sql) {
        int primeiroPontoEVirgula = sql.indexOf(';');
        if (primeiroPontoEVirgula < 0) {
            return false;
        }

        String depois = sql.substring(primeiroPontoEVirgula + 1).trim();
        return !depois.isEmpty();
    }

    private String removerComentarios(String sql) {
        String semBloco = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return semBloco.replaceAll("(?m)--.*?$", " ");
    }

    private String mascararStrings(String sql) {
        return sql.replaceAll("'([^']|'')*'", "''");
    }

    private String removerPontoEVirgulaFinal(String sql) {
        String normalizado = sql.trim();
        if (normalizado.endsWith(";")) {
            return normalizado.substring(0, normalizado.length() - 1).trim();
        }
        return normalizado;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
