package com.api_sincdb.domain.operacao.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.TipoSQLInfo;
import com.api_sincdb.util.DicionarioTipoSql;
import com.api_sincdb.util.UtilsSync;



@Service
public class CriacaoTabelaService {

    @Autowired
    private UtilsSync utilsSync;

    public String criarEstuturaTabela(Connection conexao, String nomeTabelaCompleto) throws SQLException {
        StringBuilder createTableScript = new StringBuilder();
        boolean needsUuidOssp = false;

        String schema = utilsSync.extrairSchema(nomeTabelaCompleto);
        String nomeTabela = utilsSync.extrairTabela(nomeTabelaCompleto);

        if (nomeTabelaCompleto.contains(".")) {
            String[] partes = nomeTabelaCompleto.split("\\.");
            schema = partes[0];
            nomeTabela = partes[1];
        }

        Map<String, String> serialColumns = new HashMap<>();
        serialColumns = obterSerialColuna(serialColumns, conexao, schema, nomeTabela);

        createTableScript.append("CREATE TABLE ").append(schema).append(".").append(nomeTabela).append(" (\n");

        DatabaseMetaData metaData = conexao.getMetaData();
        try (ResultSet columns = metaData.getColumns(null, schema, nomeTabela, null)) {
            while (columns.next()) {
                String colName = columns.getString("COLUMN_NAME").trim();
                String typeName = columns.getString("TYPE_NAME").trim();
                int columnSize = columns.getInt("COLUMN_SIZE");
                int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                boolean isNullable = "1".equals(columns.getString("NULLABLE"));
                String defaultValue = columns.getString("COLUMN_DEF");

                createTableScript.append("    ").append(colName).append(" ");

                createTableScript = montarEstruturaColuna(createTableScript, typeName, colName, columnSize,
                        decimalDigits, serialColumns);

                if (!isNullable) {
                    createTableScript.append(" NOT NULL");
                }

                if (serialColumns.containsKey(colName)) {
                    createTableScript.append(" DEFAULT ").append(serialColumns.get(colName));
                } else if (defaultValue != null && !defaultValue.isEmpty()) {
                    if (defaultValue.toLowerCase().contains("uuid_generate_v4()")) {
                        needsUuidOssp = true;
                        createTableScript.append(" DEFAULT uuid_generate_v4()");
                    } else {
                        // String cleanDefault = defaultValue.split("::")[0].trim();
                        // String cleanDefault = defaultValue.split("::")[0].trim();
                        createTableScript.append(" DEFAULT ").append(defaultValue);
                    }
                }

                  createTableScript.append(",\n");
            }
        }

        try (ResultSet pkRs = metaData.getPrimaryKeys(null, schema, nomeTabela)) {
            List<String> pkColumns = new ArrayList<>();
            while (pkRs.next()) {
                pkColumns.add(pkRs.getString("COLUMN_NAME"));
            }

            if (!pkColumns.isEmpty()) {
                createTableScript.append("    PRIMARY KEY (")
                        .append(String.join(", ", pkColumns))
                        .append(")\n");
            } else {
                createTableScript.setLength(createTableScript.length() - 2);
                createTableScript.append("\n");
            }
        }

        createTableScript.append(");");

        if (needsUuidOssp) {
            createTableScript.insert(0, "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";\n\n");
        }

        return createTableScript.toString();
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
            Map<String, String> serialColumns        
           ) {

        TipoSQLInfo tipoInfo = DicionarioTipoSql.getTipo(typeName);
        int max_varchar = 10485760;

        if (serialColumns.containsKey(colName)) {
            createTableScript.append("integer");
        } else {

            if (tipoInfo.isUsaTamanho()) {

                if ("varchar".equalsIgnoreCase(tipoInfo.getTipo()) && columnSize > max_varchar) {
                    createTableScript.append("text");
                } else {
                    createTableScript.append(tipoInfo.getTipo());
                    createTableScript.append("(").append(columnSize).append(")");
                }

            } else if (tipoInfo.isUsaPrecisaoEscala()) {
                createTableScript.append(tipoInfo.getTipo());
                columnSize = columnSize == 0 ? 18 : columnSize;
                decimalDigits = decimalDigits == 0 ? 2 : decimalDigits;
                createTableScript.append("(").append(columnSize).append(",").append(decimalDigits).append(")");
            } else {
                createTableScript.append(tipoInfo.getTipo());
            }
        }

        return createTableScript;
    }
}
