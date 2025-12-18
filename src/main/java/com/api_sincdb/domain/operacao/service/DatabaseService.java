package com.api_sincdb.domain.operacao.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.enums.TipoConexao;
import com.api_sincdb.util.Pair;
import com.api_sincdb.util.UtilsSync;

@Service
public class DatabaseService {

    @Autowired
    private UtilsSync utilsSync;

    @Autowired
    private ConexaoBanco conexaoBanco;

    public List<String> listarBases(String database, TipoConexao tipo) {
        List<String> bases = new ArrayList<>();

        try (Connection conexao = conexaoBanco.abrirConexao(database, tipo, "")) {
            String query = "SELECT datname FROM pg_database WHERE datistemplate = false and  datname not  in ('_dodb', 'defaultdb')";

            var stmt = conexao.createStatement();
            var rs = stmt.executeQuery(query);

            while (rs.next()) {
                bases.add(rs.getString("datname"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage(), e);
        }

        return bases;
    }

    public List<String> obterSchema(String database, String esquema, TipoConexao tipo) {
        List<String> listar = new ArrayList<>();
        if (database == null)
            return null;

        try (Connection conexao = conexaoBanco.abrirConexao(database, tipo, "");) {

            StringBuilder query = new StringBuilder(
                    "select  distinct  table_schema FROM information_schema.tables where table_schema  not in  ('pg_catalog', 'information_schema')");

            if (esquema != null && !esquema.isBlank()) {
                query.append(" AND table_schema = ?");
            }

            PreparedStatement stmt = conexao.prepareStatement(query.toString());

            if (esquema != null && !esquema.isBlank()) {
                stmt.setString(1, esquema);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String schema = rs.getString("table_schema");
                listar.add(schema);
            }

        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e.getMessage());

        }

        return listar;
    }

    public List<String> obterSchemaUnico(String database, String esquema, TipoConexao tipo) {
        List<String> listar = new ArrayList<>();
        if (database == null)
            return null;

        try (Connection conexao = conexaoBanco.abrirConexao(database, tipo, "");) {
            StringBuilder query = new StringBuilder(
                    "SELECT nspname  FROM pg_namespace WHERE nspname NOT IN ('pg_catalog', 'information_schema', 'pg_toast')");

            if (esquema != null && !esquema.isBlank()) {
                query.append("  AND nspname = ?");
            }

            PreparedStatement stmt = conexao.prepareStatement(query.toString());

            if (esquema != null && !esquema.isBlank()) {
                stmt.setString(1, esquema);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String schema = rs.getString("nspname");
                listar.add(schema);
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        if (listar.isEmpty())
            throw new RuntimeException("Não existe esquema com o nome: " + esquema);
        return listar;
    }

    public List<String> obterBanco(String database, String esquema, TipoConexao tipo) {
        List<String> listar = new ArrayList<>();
        if (database == null)
            return null;

        try (Connection conexao = conexaoBanco.abrirConexao(database, tipo, "");) {

            StringBuilder query = new StringBuilder(
                    "SELECT table_schema, table_name FROM information_schema.tables " +
                            "WHERE table_schema NOT IN ('pg_catalog', 'information_schema')");

            if (esquema != null && !esquema.isBlank()) {
                query.append(" AND table_schema = ?");
            }

            PreparedStatement stmt = conexao.prepareStatement(query.toString());

            if (esquema != null && !esquema.isBlank()) {
                stmt.setString(1, esquema);
            }

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String nomeTabela = rs.getString("table_name");
                listar.add(schema + "." + nomeTabela);
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return listar;
    }

    public boolean verificarTabelaExistente(String database, TipoConexao tipo, String tabelaNome) throws SQLException {
        try (Connection conexao = conexaoBanco.abrirConexao(database, tipo, "");) {

            String query = "SELECT EXISTS (" +
                    "SELECT 1 " +
                    "FROM information_schema.tables " +
                    "WHERE table_schema = 'public' " +
                    "AND table_name = '" + tabelaNome + "'" +
                    ");";

            var stmt = conexao.createStatement();
            var rs = stmt.executeQuery(query);

            if (rs.next()) {
                return rs.getBoolean(1);
            }

        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return false;
    }

    public boolean schemaExiste(Connection conexao, String nomeSchema) throws SQLException {
        String sql = "SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?";
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeSchema);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String gerarQueryCriacaoSchemas(Connection conexao, String schema) throws SQLException {
        String query = "";

        if (!schemaExiste(conexao, schema))
            query = "CREATE SCHEMA IF NOT EXISTS " + schema + ";";

        return query;
    }

    public String criarSequenciaQuery(Connection conexaoCloud, Connection conexaoLocal, String esquema)
            throws SQLException {
        // SELECT last_value FROM pg_sequences WHERE schemaname = 'public' AND
        // sequencename = 'alteracao_orcamentaria_id_alteracaoorcamentaria_seq';

        StringBuilder createTableScript = new StringBuilder();
        try {
            StringBuilder query = new StringBuilder(
                    "SELECT schemaname, sequencename FROM pg_sequences where schemaname = ?;");

            PreparedStatement stmt = conexaoCloud.prepareStatement(query.toString());
            stmt.setString(1, esquema);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String nomeEsquemaCloud = rs.getString("schemaname");
                String nomeSequenciaCloud = rs.getString("sequencename");
                if (!sequenciaExiste(conexaoLocal, nomeSequenciaCloud)) {
                    String sequenciaCompleta = nomeEsquemaCloud + "." + nomeSequenciaCloud;
                    String createSequenceQuery = String.format(
                            "CREATE SEQUENCE IF NOT EXISTS %s START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;",
                            sequenciaCompleta);
                    createTableScript.append(createSequenceQuery + "\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        return createTableScript.length() > 0 ? createTableScript.toString() : null;
    }

    private boolean sequenciaExiste(Connection conexao, String nomeSequencia) throws SQLException {
        String query = "SELECT COUNT(*) FROM pg_class WHERE relname = ? AND relkind = 'S'"; // 'S' para sequência
        try (PreparedStatement stmt = conexao.prepareStatement(query)) {
            stmt.setString(1, nomeSequencia.trim().toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    public List<String> criarFuncoesQuery(Connection conexaoCloud, Connection conexaoLocal) throws SQLException {
        List<String> sqlCache = new ArrayList<>();

        String queryFuncoes = """
                SELECT n.nspname AS schema_name,
                       p.proname AS function_name,
                       pg_get_function_identity_arguments(p.oid) AS arguments,
                       pg_get_functiondef(p.oid) AS function_definition
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
                  AND pg_function_is_visible(p.oid);
                """;

        try (
                Statement stmtCloud = conexaoCloud.createStatement();
                ResultSet rsCloud = stmtCloud.executeQuery(queryFuncoes)) {
            while (rsCloud.next()) {

                String schema = rsCloud.getString("schema_name");
                String nomeFuncao = rsCloud.getString("function_name");
                String argumentos = rsCloud.getString("arguments"); // assinatura ex: "integer, text"
                String definicao = rsCloud.getString("function_definition");

                // Verifica no LOCAL se a função já existe
                if (!funcaoExiste(conexaoLocal, schema, nomeFuncao, argumentos)) {

                    // Ajusta a definição para garantir CREATE OR REPLACE
                    String definicaoAjustada = ajustarCreateOrReplace(definicao);

                    sqlCache.add(definicaoAjustada);
                }
            }
        }

        return sqlCache;
    }

    private String ajustarCreateOrReplace(String definicao) {
        if (definicao == null)
            return null;

        return definicao
                .replaceFirst("CREATE FUNCTION", "CREATE OR REPLACE FUNCTION")
                .replaceFirst("create function", "CREATE OR REPLACE FUNCTION");
    }

    private boolean funcaoExiste(Connection conexao, String schema, String nomeFuncao, String argumentos)
            throws SQLException {
        String sql = """
                SELECT 1
                FROM pg_proc p
                JOIN pg_namespace n ON p.pronamespace = n.oid
                WHERE n.nspname = ?
                  AND p.proname = ?
                  AND pg_get_function_identity_arguments(p.oid) = ?;
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, nomeFuncao);
            stmt.setString(3, argumentos);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public List<String> gerarScriptsExtensoes(Connection conexaoCloud, Connection conexaoLocal) throws SQLException {
        List<String> scripts = new ArrayList<>();

        Map<String, List<Pair<String, String>>> funcoesPorExtensao = buscarFuncoesPorExtensao(conexaoCloud);

        for (Map.Entry<String, List<Pair<String, String>>> entrada : funcoesPorExtensao.entrySet()) {
            String extensao = entrada.getKey();
            List<Pair<String, String>> funcoes = entrada.getValue();

            boolean algumaFuncaoNaoExiste = false;

            for (Pair<String, String> funcao : funcoes) {
                if (!funcaoExisteExtencao(conexaoLocal, funcao.getKey(), funcao.getValue())) {
                    algumaFuncaoNaoExiste = true;
                    break;
                }
            }

            if (algumaFuncaoNaoExiste) {
                String nomeFormatado = extensao.contains("-") ? "\"" + extensao + "\"" : extensao;
                scripts.add("CREATE EXTENSION IF NOT EXISTS " + nomeFormatado + ";");
            }
        }

        return scripts;
    }

    public List<String> gerarScriptsViews(
            Connection conexaoCloud,
            Connection conexaoLocal,
            String esquema) throws SQLException {

        List<String> scripts = new ArrayList<>();

        String sql = """
                SELECT
                    c.relname AS view_name,
                    pg_get_viewdef(c.oid, true) AS view_definition
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE c.relkind = 'v'
                  AND n.nspname = ?
                ORDER BY c.relname
                """;

        try (PreparedStatement ps = conexaoCloud.prepareStatement(sql)) {
            ps.setString(1, esquema);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String viewName = rs.getString("view_name");
                    String defView = rs.getString("view_definition");

                    if (defView == null || defView.isBlank()) {
                        System.out.println("⚠ View ignorada (definição nula): " + esquema + "." + viewName);
                        continue;
                    }

                    defView = defView.trim();

                    if (!defView.regionMatches(true, 0, "select", 0, 6)) {
                        System.out.println("⚠ View ignorada (SQL inválido): " + esquema + "." + viewName);
                        continue;
                    }

                    String script = """
                            DROP VIEW IF EXISTS %s.%s CASCADE;
                            CREATE VIEW %s.%s AS
                            %s;
                            """.formatted(esquema, viewName, esquema, viewName, defView);

                    scripts.add(script);
                }
            }
        }

        return scripts;
    }

    private Map<String, List<Pair<String, String>>> buscarFuncoesPorExtensao(Connection conexao) throws SQLException {
        Map<String, List<Pair<String, String>>> map = new HashMap<>();

        String sql = """
                SELECT
                    e.extname,
                    p.proname,
                    pg_get_function_identity_arguments(p.oid) AS args
                FROM
                    pg_extension e
                JOIN
                    pg_depend d ON d.refobjid = e.oid AND d.deptype = 'e'
                JOIN
                    pg_proc p ON p.oid = d.objid
                ORDER BY
                    e.extname;
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String extensao = rs.getString("extname");
                String nomeFuncao = rs.getString("proname");
                String argumentos = rs.getString("args");

                map.computeIfAbsent(extensao, k -> new ArrayList<>())
                        .add(new Pair<>(nomeFuncao, argumentos));
            }
        }

        return map;
    }

    private boolean funcaoExisteExtencao(Connection conexao, String nomeFuncao, String argumentos) throws SQLException {
        String sql = """
                SELECT 1
                FROM pg_proc p
                WHERE p.proname = ?
                  AND pg_get_function_identity_arguments(p.oid) = ?;
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, nomeFuncao);
            stmt.setString(2, argumentos);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String obterChaveEstrangeira(Connection conexao, String nomeTabela) throws SQLException {
        String schema = utilsSync.extrairSchema(nomeTabela);
        String tabela = utilsSync.extrairTabela(nomeTabela);

        StringBuilder createForeignKeyScript = new StringBuilder();

        DatabaseMetaData metaData = conexao.getMetaData();
        ResultSet foreignKeyResultSet = metaData.getImportedKeys(null, schema, tabela);

        while (foreignKeyResultSet.next()) {
            String constraintName = foreignKeyResultSet.getString("FK_NAME");
            String columnName = foreignKeyResultSet.getString("FKCOLUMN_NAME");
            String foreignTableName = foreignKeyResultSet.getString("PKTABLE_NAME");
            String foreignColumnName = foreignKeyResultSet.getString("PKCOLUMN_NAME");
            String foreignTableSchema = foreignKeyResultSet.getString("PKTABLE_SCHEM");

            if (constraintName != null && columnName != null && foreignTableName != null && foreignColumnName != null) {

                constraintName = constraintName.replace("-", "_");

                createForeignKeyScript.append("ALTER TABLE ").append(nomeTabela)
                        .append(" ADD CONSTRAINT ").append(constraintName)
                        .append(" FOREIGN KEY (").append(columnName).append(")")
                        .append(" REFERENCES ").append(foreignTableSchema).append(".").append(foreignTableName)
                        .append(" (").append(foreignColumnName).append(");\n");
                // .append(" ON DELETE CASCADE ON UPDATE CASCADE;\n");
            }
        }

        foreignKeyResultSet.close();

        return createForeignKeyScript.toString();
    }

    public String obterIndices(Connection conexao, String nomeTabela) throws SQLException {
        StringBuilder createIndexScript = new StringBuilder();
        DatabaseMetaData metaData = conexao.getMetaData();

        Map<String, List<String>> indices = new HashMap<>();

        ResultSet indexResultSet = metaData.getIndexInfo(null, "public", nomeTabela, false, false);

        while (indexResultSet.next()) {
            String indexName = indexResultSet.getString("INDEX_NAME");
            String columnName = indexResultSet.getString("COLUMN_NAME");
            boolean nonUnique = indexResultSet.getBoolean("NON_UNIQUE");

            if (indexName != null && columnName != null) {
                indices.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                indices.put(indexName + "_type", List.of(nonUnique ? "INDEX" : "UNIQUE INDEX"));
            }
        }

        for (String indexName : indices.keySet()) {
            if (!indexName.endsWith("_type")) {
                String indexType = indices.get(indexName + "_type").get(0);
                StringJoiner columns = new StringJoiner(", ");
                indices.get(indexName).forEach(columns::add);

                createIndexScript.append("CREATE ")
                        .append(indexType)
                        .append(" ").append(indexName)
                        .append(" ON ").append(nomeTabela)
                        .append(" (").append(columns).append(");\n");
            }
        }

        indexResultSet.close();

        if (createIndexScript.length() == 0) {
            return "-- Nenhum índice encontrado para a tabela " + nomeTabela + ".\n";
        }

        return createIndexScript.toString();
    }

    public Set<String> obterTabelaMetaData(String base, Connection conexao) {
        try {
            if (conexao == null || conexao.isClosed()) {
                throw new IllegalStateException("Conexão inválida ou já fechada.");
            }

            DatabaseMetaData conexaoMetaData = conexao.getMetaData();
            ResultSet tabelas = conexaoMetaData.getTables(null, null, "%", new String[] { "TABLE" });

            Set<String> nomeTabelas = new HashSet<>();

            while (tabelas.next()) {
                String schema = tabelas.getString("TABLE_SCHEM");
                String nomeTabela = tabelas.getString("TABLE_NAME");

                nomeTabelas.add(schema + "." + nomeTabela);
            }

            return nomeTabelas;
        } catch (SQLException e) {
            throw new RuntimeException(e.getMessage());
        }

    }

}
