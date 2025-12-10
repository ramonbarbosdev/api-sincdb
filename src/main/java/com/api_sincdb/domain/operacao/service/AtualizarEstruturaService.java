package com.api_sincdb.domain.operacao.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.Coluna;
import com.api_sincdb.domain.operacao.model.ResultadoComparacao;
import com.api_sincdb.domain.operacao.model.TipoSQLInfo;
import com.api_sincdb.util.DicionarioTipoSql;
import com.api_sincdb.util.UtilsSync;

@Service
public class AtualizarEstruturaService {

    @Autowired
    private UtilsSync utilsSync;

    // ---------------------------------------------------------------------------------------------
    // Entrada principal do serviço: VERIFICAÇÃO COMPLETA DA TABELA
    // ---------------------------------------------------------------------------------------------
    public ResultadoComparacao compararEstruturaTabela(Connection conexaoCloud, Connection conexaoLocal,
            String nomeTabela) throws SQLException {

        ResultadoComparacao resultado = new ResultadoComparacao();

        Map<String, Coluna> estruturaCloud = obterEstruturaColunas(conexaoCloud, nomeTabela);
        Map<String, Coluna> estruturaLocal = obterEstruturaColunas(conexaoLocal, nomeTabela);

        compararColunas(resultado, nomeTabela, estruturaCloud, estruturaLocal);
        compararColunasRemovidas(resultado, nomeTabela, estruturaCloud, estruturaLocal);

        return resultado;
    }

    // ---------------------------------------------------------------------------------------------
    // BUSCA VIEWS DEPENDENTES (usado no processarTabelas)
    // ---------------------------------------------------------------------------------------------
    public List<String> buscarViewsDependentes(Connection conexao, String schemaTabela, String nomeTabela,
            String nomeColuna)
            throws SQLException {

        List<String> views = new ArrayList<>();

        // 1) Tentar information_schema.view_column_usage (melhor prática)
        String sql1 = """
                    SELECT view_schema, view_name
                    FROM information_schema.view_column_usage
                    WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """;

        try (PreparedStatement pst = conexao.prepareStatement(sql1)) {
            pst.setString(1, schemaTabela);
            pst.setString(2, nomeTabela);
            pst.setString(3, nomeColuna);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    views.add(rs.getString("view_schema") + "." + rs.getString("view_name"));
                }
            }
        }

        if (!views.isEmpty())
            return dedupe(views);

        // 2) Fallback: pg_depend (cobre dependências catalogadas)
        String sql2 = """
                    SELECT n.nspname AS view_schema, v.relname AS view_name
                    FROM pg_attribute a
                    JOIN pg_class t ON a.attrelid = t.oid
                    JOIN pg_depend d ON d.refobjid = t.oid AND d.refobjsubid = a.attnum
                    JOIN pg_class v ON d.objid = v.oid
                    JOIN pg_namespace n ON n.oid = v.relnamespace
                    WHERE t.relname = ? AND a.attname = ? AND v.relkind = 'v';
                """;

        try (PreparedStatement pst = conexao.prepareStatement(sql2)) {
            pst.setString(1, nomeTabela);
            pst.setString(2, nomeColuna);
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    views.add(rs.getString("view_schema") + "." + rs.getString("view_name"));
                }
            }
        }

        if (!views.isEmpty())
            return dedupe(views);

        // 3) Fallback textual: procurar o nome da coluna (ILIKE) nas definições das
        // views
        String sql3 = """
                    SELECT schemaname, viewname
                    FROM pg_views
                    WHERE definition ILIKE ?
                """;

        try (PreparedStatement pst = conexao.prepareStatement(sql3)) {
            pst.setString(1, "%" + nomeColuna + "%");
            try (ResultSet rs = pst.executeQuery()) {
                while (rs.next()) {
                    views.add(rs.getString("schemaname") + "." + rs.getString("viewname"));
                }
            }
        }

        return dedupe(views);
    }

    private List<String> dedupe(List<String> list) {
        LinkedHashSet<String> set = new LinkedHashSet<>(list);
        return new ArrayList<>(set);
    }

    // ---------------------------------------------------------------------------------------------
    // BUSCA COLUNAS DO BANCO PARA MONTAR O MAPA DE ESTRUTURA
    // ---------------------------------------------------------------------------------------------
    private Map<String, Coluna> obterEstruturaColunas(Connection conexao, String nomeTabela) throws SQLException {

        Map<String, Coluna> estrutura = new HashMap<>();
        DatabaseMetaData metaData = conexao.getMetaData();

        String schema = utilsSync.extrairSchema(nomeTabela);
        String tabela = utilsSync.extrairTabela(nomeTabela);

        try (ResultSet colunas = metaData.getColumns(null, schema, tabela, null)) {

            while (colunas.next()) {
                Coluna coluna = new Coluna();
                coluna.setNome(colunas.getString("COLUMN_NAME"));
                coluna.setNullable(colunas.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
                coluna.setDefaultValor(colunas.getString("COLUMN_DEF"));

                String tipoOriginal = colunas.getString("TYPE_NAME").toLowerCase();
                TipoSQLInfo tipoInfo = DicionarioTipoSql.getTipo(tipoOriginal);
                coluna.setTipo(tipoInfo.getTipo());

                estrutura.put(coluna.getNome(), coluna);
            }
        }

        return estrutura;
    }

    // ---------------------------------------------------------------------------------------------
    // COMPARAR COLUNAS E GERAR ALTERAÇÕES
    // ---------------------------------------------------------------------------------------------
    private void compararColunas(ResultadoComparacao resultado,
            String nomeTabela,
            Map<String, Coluna> cloud,
            Map<String, Coluna> local) {

        cloud.forEach((nomeColuna, colunaCloud) -> {

            Coluna colunaLocal = local.get(nomeColuna);

            // ----------------------------------------------------------
            // COLUNA NOVA
            // ----------------------------------------------------------
            if (colunaLocal == null) {

                resultado.getColunasNovas().add(nomeColuna);
                resultado.getColunasAlteradas().add(nomeColuna);

                boolean flAnulavel = colunaCloud.isNullable();
                String tipo = colunaCloud.getTipo();
                String defaultValor = colunaCloud.getDefaultValor();

                if (!flAnulavel && defaultValor == null) {
                    defaultValor = obterValorDefault(tipo);
                }

                resultado.getAlteracoes()
                        .add(String.format("ALTER TABLE %s ADD COLUMN %s %s;",
                                nomeTabela, nomeColuna, tipo));

                if (!flAnulavel) {
                    String valorSeguro = obterValorDefaultSeguro(tipo);

                    resultado.getAlteracoes()
                            .add(String.format("UPDATE %s SET %s = %s WHERE %s IS NULL;",
                                    nomeTabela, nomeColuna, valorSeguro, nomeColuna));

                    resultado.getAlteracoes()
                            .add(String.format("ALTER TABLE %s ALTER COLUMN %s SET NOT NULL;",
                                    nomeTabela, nomeColuna));
                }

                if (defaultValor != null) {
                    if (!defaultValor.startsWith("'") && !defaultValor.endsWith("'")
                            && (tipo.equals("varchar") || tipo.equals("text"))) {
                        defaultValor = "'" + defaultValor.replace("'", "''") + "'";
                    }

                    resultado.getAlteracoes()
                            .add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DEFAULT %s;",
                                    nomeTabela, nomeColuna, defaultValor));
                }

                return; // segue para próxima coluna
            }

            // ----------------------------------------------------------
            // COLUNA JÁ EXISTE — COMPARAR
            // ----------------------------------------------------------
            compararTipoColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
            compararNullableColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
            compararDefaultColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
        });
    }

    // ---------------------------------------------------------------------------------------------
    private void compararTipoColuna(
            ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        String tipoCloud = cloud.getTipo().toLowerCase();
        String tipoLocal = local.getTipo().toLowerCase();

        // Se tiver tamanho: varchar(50), numeric(10,2)
        if (tipoCloud.contains("(")) {

            if (!tipoCloud.equals(tipoLocal)) {
                // Tipos com tamanhos diferentes → ALTER TABLE
                resultado.getColunasAlteradas().add(nomeColuna);

                resultado.getAlteracoes().add(
                        String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s;",
                                nomeTabela, nomeColuna, tipoCloud));
            }

            return;
        }

        // Tipos sem tamanho
        if (!tipoCloud.equals(tipoLocal)) {

            String usingClause = "";

            if ((tipoCloud.equals("date") || tipoCloud.startsWith("timestamp"))
                    && tipoLocal.contains("varchar")) {
                usingClause = " USING " + nomeColuna + "::" + tipoCloud;
            }

            resultado.getColunasAlteradas().add(nomeColuna);

            resultado.getAlteracoes().add(
                    String.format("ALTER TABLE %s ALTER COLUMN %s TYPE %s%s;",
                            nomeTabela, nomeColuna, tipoCloud, usingClause));
        }
    }

    // ---------------------------------------------------------------------------------------------
    private void compararNullableColuna(ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (cloud.isNullable() != local.isNullable()) {

            resultado.getAlteracoes()
                    .add(String.format(
                            "ALTER TABLE %s ALTER COLUMN %s %s NOT NULL;",
                            nomeTabela,
                            nomeColuna,
                            cloud.isNullable() ? "DROP" : "SET"));

            resultado.getColunasAlteradas().add(nomeColuna);
        }
    }

    // ---------------------------------------------------------------------------------------------
    private void compararDefaultColuna(ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (!Objects.equals(cloud.getDefaultValor(), local.getDefaultValor())) {

            if (cloud.getDefaultValor() == null) {
                resultado.getAlteracoes()
                        .add(String.format("ALTER TABLE %s ALTER COLUMN %s DROP DEFAULT;",
                                nomeTabela, nomeColuna));
            } else {

                String defaultVal = cloud.getDefaultValor();

                if (cloud.getTipo().toLowerCase().contains("char")
                        || cloud.getTipo().equalsIgnoreCase("text")) {

                    if (!(defaultVal.startsWith("'") && defaultVal.endsWith("'"))) {
                        defaultVal = "'" + defaultVal.replace("'", "''") + "'";
                    }
                }

                resultado.getAlteracoes()
                        .add(String.format("ALTER TABLE %s ALTER COLUMN %s SET DEFAULT %s;",
                                nomeTabela, nomeColuna, defaultVal));
            }

            resultado.getColunasAlteradas().add(nomeColuna);
        }
    }

    // ---------------------------------------------------------------------------------------------
    void compararColunasRemovidas(ResultadoComparacao resultado,
            String tabela,
            Map<String, Coluna> cloud,
            Map<String, Coluna> local) {

        for (String colLocal : local.keySet()) {
            if (!cloud.containsKey(colLocal)) {

                resultado.getColunasRemovidas().add(colLocal);
                resultado.getColunasAlteradas().add(colLocal);

                resultado.getAlteracoes()
                        .add("ALTER TABLE " + tabela + " DROP COLUMN " + colLocal + ";");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // VALORES DEFAULT INTERNOS
    // ---------------------------------------------------------------------------------------------
    private String obterValorDefault(String tipo) {
        switch (tipo.toLowerCase()) {
            case "boolean":
            case "bool":
                return "false";
            case "int":
            case "integer":
            case "bigint":
            case "smallint":
                return "0";
            case "numeric":
            case "decimal":
            case "float":
            case "double":
                return "0.0";
            case "timestamp":
            case "timestamptz":
                return "'1970-01-01 00:00:00'";
            case "date":
                return "'1970-01-01'";
            case "text":
            case "varchar":
            case "char":
                return "''";
            default:
                return "null";
        }
    }

    private String obterValorDefaultSeguro(String tipo) {
        tipo = tipo.toLowerCase();

        if (tipo.contains("int"))
            return "0";
        if (tipo.contains("char") || tipo.contains("text"))
            return "''";
        if (tipo.contains("bool"))
            return "false";
        if (tipo.contains("date"))
            return "'1970-01-01'";
        if (tipo.contains("timestamp"))
            return "'1970-01-01 00:00:00'";
        if (tipo.contains("float") || tipo.contains("double") || tipo.contains("numeric") || tipo.contains("real"))
            return "0.0";

        return "null";
    }

    public String obterDefinicaoView(Connection conexao, String schema, String viewName) throws SQLException {

        String sql = """
                    SELECT pg_get_viewdef(format('%I.%I', ?, ?)::regclass, true) AS view_definition
                """;

        try (PreparedStatement pst = conexao.prepareStatement(sql)) {
            pst.setString(1, schema);
            pst.setString(2, viewName);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("view_definition");
                }
            }
        }

        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Entrada principal do serviço: VERIFICAÇÃO COMPLETA DA TABELA
    // ---------------------------------------------------------------------------------------------
    public void gerarScriptsViewsDependentes(
            Connection conexaoCloud,
            Connection conexaoLocal,
            String nomeTabela,
            ResultadoComparacao resultado,
            List<String> dropViewsDependentes,
            List<String> createViewsDependentes) throws SQLException {

        String schemaTabela = utilsSync.extrairSchema(nomeTabela);
        String tabela = utilsSync.extrairTabela(nomeTabela);

        for (String colunaAlterada : resultado.getColunasAlteradas()) {

            // 1. Buscar se existem views dependentes desta coluna
            List<String> viewsDep = buscarViewsDependentes(
                    conexaoLocal,
                    schemaTabela,
                    tabela,
                    colunaAlterada);

            // 2. Para cada view dependente gerar DROP + CREATE
            for (String viewName : viewsDep) {

                // DROP VIEW
                dropViewsDependentes.add("DROP VIEW IF EXISTS " + viewName + ";");

                // Extrair schema e nome da view
                String schemaView = utilsSync.extrairSchema(viewName);
                String nomeView = utilsSync.extrairTabela(viewName);

                // Obter definição oficial da view no cloud
                String defView = obterDefinicaoView(conexaoCloud, schemaView, nomeView);

                // CREATE VIEW
                createViewsDependentes.add(
                        "CREATE OR REPLACE VIEW " + viewName + " AS " + defView + ";");
            }
        }
    }
}
