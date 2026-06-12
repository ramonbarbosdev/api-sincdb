package com.api_sincdb.domain.sql.service;

import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SqlSecurityValidator {

    public static final int MAX_SQL_LENGTH = 20000;
    public static final int MAX_ROWS = 500;
    public static final int QUERY_TIMEOUT_SECONDS = 30;

    private static final Pattern BLOCKED_COMMANDS = Pattern.compile(
            "\\b(drop|truncate|delete|update|insert|alter|create|grant|revoke|copy|call|execute)\\b",
            Pattern.CASE_INSENSITIVE);

    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw badRequest("SQL nao pode ser vazio.");
        }

        if (sql.length() > MAX_SQL_LENGTH) {
            throw badRequest("SQL excede o tamanho maximo permitido.");
        }

        String sqlSemComentarios = removerComentarios(sql);
        String sqlParaValidacao = mascararStrings(sqlSemComentarios).trim();

        if (sqlParaValidacao.isBlank()) {
            throw badRequest("SQL nao pode ser vazio.");
        }

        if (!sqlParaValidacao.toLowerCase().startsWith("select")) {
            throw badRequest("Neste momento apenas consultas SELECT sao permitidas.");
        }

        if (BLOCKED_COMMANDS.matcher(sqlParaValidacao).find()) {
            throw badRequest("Comando SQL nao permitido.");
        }

        if (contemMultiplasInstrucoes(sqlParaValidacao)) {
            throw badRequest("Multiplas instrucoes SQL nao sao permitidas.");
        }

        if (!Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE).matcher(sqlParaValidacao).find()) {
            throw badRequest("Consulta SELECT deve conter LIMIT.");
        }

        return removerPontoEVirgulaFinal(sqlSemComentarios.trim());
    }

    private String removerComentarios(String sql) {
        String semBloco = sql.replaceAll("(?s)/\\*.*?\\*/", " ");
        return semBloco.replaceAll("(?m)--.*?$", " ");
    }

    private String mascararStrings(String sql) {
        return sql.replaceAll("'([^']|'')*'", "''");
    }

    private boolean contemMultiplasInstrucoes(String sql) {
        int primeiroPontoEVirgula = sql.indexOf(';');
        if (primeiroPontoEVirgula < 0) {
            return false;
        }

        String depois = sql.substring(primeiroPontoEVirgula + 1).trim();
        return !depois.isEmpty();
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
