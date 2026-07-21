package com.api_sincdb.domain.operacao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.Date;

@Service
public class InsertSqlBuilderService {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String construirInsertSQL(String tabela, ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int colCount = meta.getColumnCount();

        boolean temIdentity = false;
        for (int i = 1; i <= colCount; i++) {
            if (meta.isAutoIncrement(i)) {
                temIdentity = true;
                break;
            }
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tabela)
                .append(" (");

        // Campos
        for (int i = 1; i <= colCount; i++) {
            sql.append(meta.getColumnName(i));
            if (i < colCount)
                sql.append(", ");
        }

        // Gera ALWAYS AS IDENTITY rejeita valor explícito sem OVERRIDING SYSTEM VALUE
        sql.append(") ");
        if (temIdentity) {
            sql.append("OVERRIDING SYSTEM VALUE ");
        }
        sql.append("VALUES (");

        // Valores
        for (int i = 1; i <= colCount; i++) {
            Object value = rs.getObject(i);
            String columnType = meta.getColumnTypeName(i);

            sql.append(convertValue(value, columnType));

            if (i < colCount)
                sql.append(", ");
        }

        sql.append(");");
        return sql.toString();
    }

    // ================================================================
    // CONVERSÃO CENTRALIZADA
    // ================================================================
    private static String convertValue(Object value, String sqlType) throws SQLException {

        if (value == null)
            return "NULL";

        // ----------------------------------------------------
        // Trata PGobject (caso venha como json/jsonb)
        // ----------------------------------------------------
        if (value instanceof PGobject pg) {

            String tipo = pg.getType();
            String conteudo = pg.getValue();

            if ("json".equalsIgnoreCase(tipo) || "jsonb".equalsIgnoreCase(tipo)) {

                if (looksLikeJson(conteudo)) {
                    String normalized = normalizeJsonStrict(conteudo);
                    return quoteJson(normalized);
                }

                return "'" + escape(conteudo) + "'";
            }

            return "'" + escape(conteudo) + "'";
        }

        // ----------------------------------------------------
        // Campos TEXT/VARCHAR — sempre tratar como texto, nunca como JSON
        // ----------------------------------------------------
        if (value instanceof String s) {

            if (isJsonColumn(sqlType)) {
                if (looksLikeJson(s)) {
                    String normalized = normalizeJsonStrict(s);
                    return quoteJson(normalized);
                }
            }

            return "'" + escape(s) + "'";
        }

        // ----------------------------------------------------
        // Datas
        // ----------------------------------------------------
        if (value instanceof Timestamp t) {
            return "'" + t.toString() + "'";
        }
        if (value instanceof java.sql.Date d) {
            return "'" + d.toString() + "'";
        }
        if (value instanceof java.util.Date d) {
            return "'" + new Timestamp(d.getTime()) + "'";
        }
        if (value instanceof LocalDate ld) {
            return "'" + ld + "'";
        }
        if (value instanceof LocalDateTime ldt) {
            return "'" + ldt + "'";
        }
        if (value instanceof OffsetDateTime odt) {
            return "'" + odt + "'";
        }

        // ----------------------------------------------------
        // Numéricos
        // ----------------------------------------------------
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }

        // ----------------------------------------------------
        // Boolean
        // ----------------------------------------------------
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }

        // ----------------------------------------------------
        // Enum
        // ----------------------------------------------------
        if (value instanceof Enum<?> e) {
            return "'" + escape(e.name()) + "'";
        }

        // ----------------------------------------------------
        // BYTEA
        // ----------------------------------------------------
        if (value instanceof byte[] bytes) {
            return "E'\\\\x" + bytesToHex(bytes) + "'";
        }

        // ----------------------------------------------------
        // Default
        // ----------------------------------------------------
        return "'" + escape(value.toString()) + "'";
    }

    // ================================================================
    // JSON — apenas quando a coluna for json/jsonb
    // ================================================================
    private static boolean isJsonColumn(String sqlType) {
        if (sqlType == null)
            return false;
        return sqlType.equalsIgnoreCase("json") || sqlType.equalsIgnoreCase("jsonb");
    }

    private static boolean looksLikeJson(String s) {
        if (s == null)
            return false;
        s = s.trim();
        return (s.startsWith("{") && s.endsWith("}")) ||
                (s.startsWith("[") && s.endsWith("]"));
    }

    private static String normalizeJsonStrict(String raw) throws SQLException {

        // primeira tentativa: JSON está válida -> retorna como está
        try {
            mapper.readTree(raw);
            return raw;
        } catch (Exception ignored) {
        }

        // tntar corrigir aspas duplas acidentais "" -> " somente se isso resolver
        String fixed1 = raw.replaceAll("(?<!\\\\)\"\"", "\"");
        try {
            mapper.readTree(fixed1);
            return mapper.writeValueAsString(mapper.readTree(fixed1));
        } catch (Exception ignored) {
        }

        // tentar remover escapes quebrados
        String fixed2 = raw.replace("\\\"", "\"");
        try {
            mapper.readTree(fixed2);
            return mapper.writeValueAsString(mapper.readTree(fixed2));
        } catch (Exception ignored) {
        }

        // ultima tentativa: restaurar aspas corretas ao redor de chaves/arrays
        String fixed3 = raw.trim();
        if (fixed3.startsWith("\"") && fixed3.endsWith("\"")) {
            fixed3 = fixed3.substring(1, fixed3.length() - 1);
        }

        try {
            mapper.readTree(fixed3);
            return mapper.writeValueAsString(mapper.readTree(fixed3));
        } catch (Exception ignored) {
        }

        // nada funcionou -> JSON definitivamente inválido
        throw new SQLException("JSON inválido e não pôde ser normalizado: " + raw);
    }

    private static String quoteJson(String json) {
        String tag = "json" + System.nanoTime();
        return "$" + tag + "$" + json + "$" + tag + "$::jsonb";
    }

    // ================================================================
    // UTILITÁRIOS
    // ================================================================
    private static String escape(String s) {
        if (s == null)
            return null;
        return s.replace("\\", "\\\\")
                .replace("'", "''");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
