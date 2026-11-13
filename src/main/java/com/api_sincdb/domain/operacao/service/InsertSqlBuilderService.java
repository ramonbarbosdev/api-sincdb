package com.api_sincdb.domain.operacao.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class InsertSqlBuilderService {

    private static final ObjectMapper mapper = new ObjectMapper();

    public String construirInsertSQL(String tabela, ResultSet rs) throws SQLException {
        return construirInsertSQL(tabela, rs, Set.of());
    }

    public String construirInsertSQL(String tabela, ResultSet rs, Set<String> colunasExcluir) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        List<Integer> indices = indicesColunasInclusas(meta, colunasExcluir);
        if (indices.isEmpty()) {
            throw new SQLException("Nenhuma coluna disponível para INSERT em " + tabela);
        }

        boolean temIdentity = false;
        for (int index : indices) {
            if (meta.isAutoIncrement(index)) {
                temIdentity = true;
                break;
            }
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tabela)
                .append(" (");

        for (int pos = 0; pos < indices.size(); pos++) {
            sql.append(meta.getColumnName(indices.get(pos)));
            if (pos < indices.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(") ");
        if (temIdentity) {
            sql.append("OVERRIDING SYSTEM VALUE ");
        }
        sql.append("VALUES (");

        for (int pos = 0; pos < indices.size(); pos++) {
            int index = indices.get(pos);
            Object value = rs.getObject(index);
            String columnType = meta.getColumnTypeName(index);
            sql.append(convertValue(value, columnType));
            if (pos < indices.size() - 1) {
                sql.append(", ");
            }
        }

        sql.append(");");
        return sql.toString();
    }

    static List<Integer> indicesColunasInclusas(ResultSetMetaData meta, Set<String> colunasExcluir)
            throws SQLException {
        Set<String> excluir = colunasExcluir == null ? Set.of() : colunasExcluir;
        Set<String> excluirLower = new HashSet<>();
        for (String nome : excluir) {
            if (nome != null && !nome.isBlank()) {
                excluirLower.add(nome.trim().toLowerCase(Locale.ROOT));
            }
        }

        List<Integer> indices = new ArrayList<>();
        int colCount = meta.getColumnCount();
        for (int i = 1; i <= colCount; i++) {
            String nome = meta.getColumnName(i);
            if (nome != null && excluirLower.contains(nome.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            indices.add(i);
        }
        return indices;
    }

    private static String convertValue(Object value, String sqlType) throws SQLException {

        if (value == null)
            return "NULL";

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

        if (value instanceof String s) {

            if (isJsonColumn(sqlType)) {
                if (looksLikeJson(s)) {
                    String normalized = normalizeJsonStrict(s);
                    return quoteJson(normalized);
                }
            }

            return "'" + escape(s) + "'";
        }

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

        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }

        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }

        if (value instanceof Enum<?> e) {
            return "'" + escape(e.name()) + "'";
        }

        if (value instanceof byte[] bytes) {
            return "E'\\\\x" + bytesToHex(bytes) + "'";
        }

        return "'" + escape(value.toString()) + "'";
    }

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

        try {
            mapper.readTree(raw);
            return raw;
        } catch (Exception ignored) {
        }

        String fixed1 = raw.replaceAll("(?<!\\\\)\"\"", "\"");
        try {
            mapper.readTree(fixed1);
            return mapper.writeValueAsString(mapper.readTree(fixed1));
        } catch (Exception ignored) {
        }

        String fixed2 = raw.replace("\\\"", "\"");
        try {
            mapper.readTree(fixed2);
            return mapper.writeValueAsString(mapper.readTree(fixed2));
        } catch (Exception ignored) {
        }

        String fixed3 = raw.trim();
        if (fixed3.startsWith("\"") && fixed3.endsWith("\"")) {
            fixed3 = fixed3.substring(1, fixed3.length() - 1);
        }

        try {
            mapper.readTree(fixed3);
            return mapper.writeValueAsString(mapper.readTree(fixed3));
        } catch (Exception ignored) {
        }

        throw new SQLException("JSON inválido e não pôde ser normalizado: " + raw);
    }

    private static String quoteJson(String json) {
        String tag = "json" + System.nanoTime();
        return "$" + tag + "$" + json + "$" + tag + "$::jsonb";
    }

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
