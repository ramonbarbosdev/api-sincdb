package com.api_sincdb.domain.operacao.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.TipoSQLInfo;
import com.api_sincdb.util.DicionarioTipoSql;
import com.api_sincdb.util.UtilsSync;

@Service
public class CriacaoTabelaEstruturaService {

    @Autowired
    private UtilsSync utilsSync;

    public String criarEstruturaTabela(Connection conexao, String nomeTabelaCompleto) throws SQLException {

        String schema = utilsSync.extrairSchema(nomeTabelaCompleto);
        String nomeTabela = utilsSync.extrairTabela(nomeTabelaCompleto);

        DatabaseMetaData metaData = conexao.getMetaData();

        List<String> colunasDDL = new ArrayList<>();
        boolean needsUuidOssp = false;

        // Detecta colunas auto-incremento
        Set<String> colunasIdentity = obterColunasIdentity(conexao, schema, nomeTabela);

        try (ResultSet columns = metaData.getColumns(null, schema, nomeTabela, null)) {

            while (columns.next()) {

                String colName = columns.getString("COLUMN_NAME");
                String typeName = columns.getString("TYPE_NAME");
                int columnSize = columns.getInt("COLUMN_SIZE");
                int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                boolean isNullable = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                String defaultValue = columns.getString("COLUMN_DEF");

                StringBuilder coluna = new StringBuilder();
                coluna.append(colName).append(" ");

                // ---- TIPO ----
                montarTipoColuna(
                        coluna,
                        typeName,
                        columnSize,
                        decimalDigits,
                        colunasIdentity.contains(colName));

                // ---- NULLABLE ----
                if (!isNullable) {
                    coluna.append(" NOT NULL");
                }

                // ---- DEFAULT (somente se NÃO for identity) ----
                if (!colunasIdentity.contains(colName)
                        && defaultValue != null
                        && !defaultValue.isBlank()) {

                    if (defaultValue.toLowerCase().contains("uuid_generate_v4")) {
                        needsUuidOssp = true;
                        coluna.append(" DEFAULT uuid_generate_v4()");
                    } else {
                        coluna.append(" DEFAULT ").append(defaultValue);
                    }
                }

                colunasDDL.add(coluna.toString());
            }
        }

        // ---- PRIMARY KEY ----
        List<String> pkColumns = new ArrayList<>();
        try (ResultSet pkRs = metaData.getPrimaryKeys(null, schema, nomeTabela)) {
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }
        }

        if (!pkColumns.isEmpty()) {
            colunasDDL.add(
                    "PRIMARY KEY (" + String.join(", ", pkColumns) + ")");
        }

        // ---- MONTA SQL FINAL ----
        StringBuilder sql = new StringBuilder();

        if (needsUuidOssp) {
            sql.append("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n");
        }

        sql.append("CREATE TABLE ")
                .append(schema).append(".").append(nomeTabela)
                .append(" (\n    ")
                .append(String.join(",\n    ", colunasDDL))
                .append("\n);");

        return sql.toString();
    }

    private Set<String> obterColunasIdentity(
            Connection conexao,
            String schema,
            String tabela) throws SQLException {

        Set<String> resultado = new HashSet<>();

        String sql = """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND (
                      is_identity = 'YES'
                      OR column_default LIKE 'nextval(%'
                  )
                """;

        try (PreparedStatement ps = conexao.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tabela);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(rs.getString("column_name"));
                }
            }
        }

        return resultado;
    }

    private void montarTipoColuna(
            StringBuilder coluna,
            String typeName,
            int columnSize,
            int decimalDigits,
            boolean isIdentity) {

        TipoSQLInfo tipoInfo = DicionarioTipoSql.getTipo(typeName);

        if (isIdentity) {
            // PostgreSQL moderno
            coluna.append("integer GENERATED BY DEFAULT AS IDENTITY");
            return;
        }

        if (tipoInfo.isUsaTamanho()) {

            if ("varchar".equalsIgnoreCase(tipoInfo.getTipo()) && columnSize > 10485760) {
                coluna.append("text");
            } else {
                coluna.append(tipoInfo.getTipo())
                        .append("(").append(columnSize).append(")");
            }

        } else if (tipoInfo.isUsaPrecisaoEscala()) {

            int precisao = columnSize > 0 ? columnSize : 18;
            int escala = decimalDigits > 0 ? decimalDigits : 2;

            coluna.append(tipoInfo.getTipo())
                    .append("(").append(precisao).append(",").append(escala).append(")");

        } else {
            coluna.append(tipoInfo.getTipo());
        }
    }

    public Map<String, String> obterSerialColuna(Map<String, String> serialColumns, Connection conexao, String schema,
            String nomeTabela) throws SQLException {
        try (PreparedStatement stmt = conexao.prepareStatement(
                "SELECT column_name, column_default FROM information_schema.columns " +
                        "WHERE table_schema = ? AND table_name = ? AND column_default LIKE 'nextval%'")) {
            stmt.setString(1, schema);
            stmt.setString(2, nomeTabela);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String colName = rs.getString("column_name");
                String rawDefault = rs.getString("column_default");
                String clean = rawDefault.replace("::regclass", "").replace("nextval(", "").replace("'", "")
                        .replace(")", "").trim();
                String seqDef = "nextval('" + clean + "'::regclass)";
                serialColumns.put(colName, seqDef);
            }
        }

        return serialColumns;
    }

    public StringBuilder montarEstruturaColuna(
            StringBuilder createTableScript,
            String typeName,
            String colName,
            int columnSize,
            int decimalDigits,
            Map<String, String> serialColumns) {

        TipoSQLInfo tipoInfo = DicionarioTipoSql.getTipo(typeName);
        int max_varchar = 10485760;

        if (serialColumns.containsKey(colName)) {
            // Auto incremento SEM DEFAULT
            createTableScript.append("integer");
            return createTableScript;
        }

        if (tipoInfo.isUsaTamanho()) {

            if ("varchar".equalsIgnoreCase(tipoInfo.getTipo()) && columnSize > max_varchar) {
                createTableScript.append("text");
            } else {
                createTableScript.append(tipoInfo.getTipo())
                        .append("(").append(columnSize).append(")");
            }

        } else if (tipoInfo.isUsaPrecisaoEscala()) {

            createTableScript.append(tipoInfo.getTipo());
            columnSize = columnSize == 0 ? 18 : columnSize;
            decimalDigits = decimalDigits == 0 ? 2 : decimalDigits;

            createTableScript.append("(")
                    .append(columnSize)
                    .append(",")
                    .append(decimalDigits)
                    .append(")");

        } else {
            createTableScript.append(tipoInfo.getTipo());
        }

        return createTableScript;
    }
}
