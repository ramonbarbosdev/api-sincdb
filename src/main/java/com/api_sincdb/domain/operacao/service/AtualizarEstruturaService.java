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
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.operacao.model.Coluna;
import com.api_sincdb.domain.operacao.model.ResultadoComparacao;
import com.api_sincdb.domain.operacao.model.TipoSQLInfo;
import com.api_sincdb.util.DicionarioTipoSql;
import com.api_sincdb.util.MetadadosColunaPostgres;
import com.api_sincdb.util.UtilsSync;

@Service
public class AtualizarEstruturaService {

    @Autowired
    private UtilsSync utilsSync;

    // =========================================================
    // ENTRADA PRINCIPAL
    // =========================================================
    public ResultadoComparacao compararEstruturaTabela(
            Connection conexaoCloud,
            Connection conexaoLocal,
            String nomeTabela) throws SQLException {

        ResultadoComparacao resultado = new ResultadoComparacao();

        Map<String, Coluna> estruturaCloud = obterEstruturaColunas(conexaoCloud, nomeTabela);
        Map<String, Coluna> estruturaLocal = obterEstruturaColunas(conexaoLocal, nomeTabela);

        compararColunas(resultado, nomeTabela, estruturaCloud, estruturaLocal);
        compararColunasRemovidas(resultado, nomeTabela, estruturaCloud, estruturaLocal);

        return resultado;
    }

    // =========================================================
    // OBTÉM ESTRUTURA DAS COLUNAS (PONTO CRÍTICO)
    // =========================================================
    private Map<String, Coluna> obterEstruturaColunas(
            Connection conexao,
            String nomeTabela) throws SQLException {

        Map<String, Coluna> estrutura = new HashMap<>();
        DatabaseMetaData metaData = conexao.getMetaData();

        String schema = utilsSync.extrairSchema(nomeTabela);
        String tabela = utilsSync.extrairTabela(nomeTabela);

        Set<String> colunasIdentity = MetadadosColunaPostgres.carregarColunasIdentity(conexao, schema, tabela);
        Map<String, String> colunasGeradas = MetadadosColunaPostgres.carregarExpressoesGeradas(
                conexao, schema, tabela);

        try (ResultSet colunas = metaData.getColumns(null, schema, tabela, null)) {

            while (colunas.next()) {

                Coluna coluna = new Coluna();
                String nomeColuna = colunas.getString("COLUMN_NAME");
                coluna.setNome(nomeColuna);
                coluna.setNullable(
                        colunas.getInt("NULLABLE") == DatabaseMetaData.columnNullable);

                String defaultValor = colunas.getString("COLUMN_DEF");
                coluna.setDefaultValor(defaultValor);

                String tipoOriginal = colunas.getString("TYPE_NAME").toLowerCase();

                if (tipoOriginal.startsWith("serial")) {
                    tipoOriginal = "integer";
                }

                boolean autoIncrement = colunasIdentity.contains(nomeColuna)
                        || (defaultValor != null && defaultValor.toLowerCase().startsWith("nextval("));
                coluna.setAutoIncrement(autoIncrement);

                String expressaoGerada = colunasGeradas.get(nomeColuna);
                if (expressaoGerada != null) {
                    coluna.setGerada(true);
                    coluna.setExpressaoGerada(expressaoGerada);
                    coluna.setDefaultValor(null);
                }

                TipoSQLInfo tipoInfo = DicionarioTipoSql.getTipo(tipoOriginal);
                coluna.setTipo(tipoInfo.getTipo());

                estrutura.put(coluna.getNome(), coluna);
            }
        }

        return estrutura;
    }

    // =========================================================
    // COMPARAÇÃO DE COLUNAS
    // =========================================================
    private void compararColunas(
            ResultadoComparacao resultado,
            String nomeTabela,
            Map<String, Coluna> cloud,
            Map<String, Coluna> local) {

        cloud.forEach((nomeColuna, colunaCloud) -> {

            Coluna colunaLocal = local.get(nomeColuna);

            // -------------------------------
            // COLUNA NOVA
            // -------------------------------
            if (colunaLocal == null) {

                resultado.getColunasNovas().add(nomeColuna);
                resultado.getColunasAlteradas().add(nomeColuna);

                StringBuilder addColumn = new StringBuilder()
                        .append("ALTER TABLE ").append(nomeTabela)
                        .append(" ADD COLUMN ").append(nomeColuna)
                        .append(" ").append(colunaCloud.getTipo());

                if (colunaCloud.isGerada()) {
                    addColumn.append(
                            MetadadosColunaPostgres.clausulaGeradaArmazenada(colunaCloud.getExpressaoGerada()));
                } else if (!colunaCloud.isAutoIncrement()
                        && colunaCloud.getDefaultValor() != null
                        && !colunaCloud.getDefaultValor().isBlank()) {
                    addColumn.append(" DEFAULT ").append(colunaCloud.getDefaultValor());
                }

                addColumn.append(";");
                resultado.getAlteracoes().add(addColumn.toString());

                // NOT NULL exige valores — backfill automático do cloud na sincronização
                if (!colunaCloud.isNullable()) {
                    if (colunaCloud.isGerada()
                            || colunaCloud.isAutoIncrement()
                            || colunaCloud.getDefaultValor() != null
                            && !colunaCloud.getDefaultValor().isBlank()) {
                        resultado.getAlteracoes().add(
                                String.format(
                                        "ALTER TABLE %s ALTER COLUMN %s SET NOT NULL;",
                                        nomeTabela,
                                        nomeColuna));
                    } else {
                        resultado.adicionarPendenteNotNull(nomeTabela, nomeColuna);
                    }
                }

                return;
            }

            compararTipoColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
            compararNullableColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
            compararDefaultColuna(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
            compararColunaGerada(resultado, nomeTabela, nomeColuna, colunaCloud, colunaLocal);
        });
    }

    // =========================================================
    // COMPARA TIPO
    // =========================================================
    private void compararTipoColuna(
            ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (cloud.isAutoIncrement() || local.isAutoIncrement() || cloud.isGerada() || local.isGerada()) {
            return;
        }

        if (cloud.getTipo().equalsIgnoreCase(local.getTipo())) {
            return;
        }

        resultado.getColunasAlteradas().add(nomeColuna);

        resultado.getAlteracoes().add(
                String.format(
                        "ALTER TABLE %s ALTER COLUMN %s TYPE %s USING %s::%s;",
                        nomeTabela,
                        nomeColuna,
                        cloud.getTipo(),
                        nomeColuna,
                        cloud.getTipo()));
    }

    // =========================================================
    // COMPARA NULLABLE
    // =========================================================
    private void compararNullableColuna(
            ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (cloud.isNullable() != local.isNullable()) {

            resultado.getColunasAlteradas().add(nomeColuna);

            if (cloud.isNullable()) {
                resultado.getAlteracoes().add(
                        String.format(
                                "ALTER TABLE %s ALTER COLUMN %s DROP NOT NULL;",
                                nomeTabela,
                                nomeColuna));
            } else {
                resultado.adicionarPendenteNotNull(nomeTabela, nomeColuna);
            }
        }
    }

    // =========================================================
    // COMPARA DEFAULT (PONTO DO ERRO ORIGINAL)
    // =========================================================
    private void compararDefaultColuna(
            ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (cloud.isAutoIncrement() || local.isAutoIncrement() || cloud.isGerada() || local.isGerada()) {
            return;
        }

        if (Objects.equals(cloud.getDefaultValor(), local.getDefaultValor())) {
            return;
        }

        resultado.getColunasAlteradas().add(nomeColuna);

        if (cloud.getDefaultValor() == null) {
            resultado.getAlteracoes().add(
                    String.format(
                            "ALTER TABLE %s ALTER COLUMN %s DROP DEFAULT;",
                            nomeTabela,
                            nomeColuna));
        } else {
            resultado.getAlteracoes().add(
                    String.format(
                            "ALTER TABLE %s ALTER COLUMN %s SET DEFAULT %s;",
                            nomeTabela,
                            nomeColuna,
                            cloud.getDefaultValor()));
        }
    }

    private void compararColunaGerada(
            ResultadoComparacao resultado,
            String nomeTabela,
            String nomeColuna,
            Coluna cloud,
            Coluna local) {

        if (!cloud.isGerada()) {
            if (local.isGerada()) {
                resultado.getColunasAlteradas().add(nomeColuna);
                resultado.getAlteracoes().add(
                        String.format(
                                "ALTER TABLE %s ALTER COLUMN %s DROP EXPRESSION;",
                                nomeTabela,
                                nomeColuna));
            }
            return;
        }

        String expressaoCloud = cloud.getExpressaoGerada() != null ? cloud.getExpressaoGerada().trim() : "";
        if (expressaoCloud.isBlank()) {
            return;
        }

        if (!local.isGerada()) {
            resultado.getColunasAlteradas().add(nomeColuna);
            if (local.getDefaultValor() != null && !local.getDefaultValor().isBlank()) {
                resultado.getAlteracoes().add(
                        String.format(
                                "ALTER TABLE %s ALTER COLUMN %s DROP DEFAULT;",
                                nomeTabela,
                                nomeColuna));
            }
            resultado.getAlteracoes().add(
                    String.format(
                            "ALTER TABLE %s ALTER COLUMN %s ADD GENERATED ALWAYS AS (%s) STORED;",
                            nomeTabela,
                            nomeColuna,
                            expressaoCloud));
            return;
        }

        String expressaoLocal = local.getExpressaoGerada() != null ? local.getExpressaoGerada().trim() : "";
        if (expressaoCloud.equalsIgnoreCase(expressaoLocal)) {
            return;
        }

        resultado.getColunasAlteradas().add(nomeColuna);
        resultado.getAlteracoes().add(
                String.format(
                        "ALTER TABLE %s ALTER COLUMN %s SET EXPRESSION AS (%s);",
                        nomeTabela,
                        nomeColuna,
                        expressaoCloud));
    }

    // =========================================================
    // COLUNAS REMOVIDAS
    // =========================================================
    private void compararColunasRemovidas(
            ResultadoComparacao resultado,
            String tabela,
            Map<String, Coluna> cloud,
            Map<String, Coluna> local) {

        for (String colLocal : local.keySet()) {
            if (!cloud.containsKey(colLocal)) {

                resultado.getColunasRemovidas().add(colLocal);
                resultado.getColunasAlteradas().add(colLocal);

                resultado.getAlteracoes().add(
                        "ALTER TABLE " + tabela + " DROP COLUMN " + colLocal + ";");
            }
        }
    }

    // =========================================================
    // GERAR OS SCRIPTS DE VIEWS QUE SAO DEPENDENTES
    // =========================================================
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

                String schemaView = utilsSync.extrairSchema(viewName);
                String nomeView = utilsSync.extrairTabela(viewName);

                if (!isView(conexaoCloud, schemaView, nomeView)) {
                    continue;
                }

                if (viewName.contains("public.registro_credito_operacionalidade_view")) {
                    System.out.println(viewName);
                }

                // DROP VIEW
                dropViewsDependentes.add(
                        "DROP VIEW IF EXISTS " + viewName + " CASCADE;");

                String defView = obterDefinicaoView(conexaoCloud, schemaView, nomeView);

                if (defView == null || defView.isBlank()) {
                    System.out.println(
                            "⚠ View ignorada (definição nula): " + viewName);
                    continue;
                }

                defView = defView.trim();

                // CREATE VIEW
                createViewsDependentes.add(
                        """
                                CREATE OR REPLACE VIEW %s AS
                                %s;
                                """.formatted(viewName, defView));
            }
        }
    }

    // =========================================================
    // FUNCOEES AUXILIARES
    // =========================================================
    public boolean isView(Connection conn, String schema, String viewName) throws SQLException {
        // Ignore catálogos internos
        if (schema.equalsIgnoreCase("pg_catalog") ||
                schema.equalsIgnoreCase("information_schema") ||
                schema.startsWith("pg_") ||
                viewName.startsWith("pg_")) {
            return false;
        }

        String sql = """
                    SELECT COUNT(*)
                    FROM information_schema.views
                    WHERE table_schema = ?
                      AND table_name = ?
                """;

        try (PreparedStatement pst = conn.prepareStatement(sql)) {
            pst.setString(1, schema);
            pst.setString(2, viewName);

            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1) > 0;
            }
        }
        return false;
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

}
