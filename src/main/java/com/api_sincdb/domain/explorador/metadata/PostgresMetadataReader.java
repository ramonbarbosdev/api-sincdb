package com.api_sincdb.domain.explorador.metadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class PostgresMetadataReader implements TableMetadataReader, ForeignKeyMetadataReader, IndexMetadataReader {

    public List<String> listarBases(Connection conexao) throws SQLException {
        String sql = """
                select datname
                from pg_database
                where datistemplate = false
                  and datname not in ('_dodb', 'defaultdb')
                order by datname
                """;

        List<String> bases = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bases.add(rs.getString("datname"));
            }
        }
        return bases;
    }

    public List<String> listarSchemas(Connection conexao) throws SQLException {
        String sql = """
                select schema_name
                from information_schema.schemata
                where schema_name not in ('pg_catalog', 'information_schema', 'pg_toast')
                order by schema_name
                """;

        List<String> schemas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                schemas.add(rs.getString("schema_name"));
            }
        }
        return schemas;
    }

    public BancoSnapshot carregarSnapshot(Connection conexao, String schemaFiltro, boolean incluirIndices,
            boolean incluirFks) throws SQLException {
        Map<String, TabelaInfo> tabelas = lerTabelas(conexao, schemaFiltro);
        carregarPrimaryKeys(conexao, schemaFiltro, tabelas);
        carregarColunas(conexao, schemaFiltro, tabelas);

        if (incluirFks) {
            carregarForeignKeys(conexao, schemaFiltro, tabelas);
        }

        if (incluirIndices) {
            carregarIndices(conexao, schemaFiltro, tabelas);
        }

        return new BancoSnapshot(tabelas);
    }

    @Override
    public Map<String, TabelaInfo> lerTabelas(Connection conexao, String schemaFiltro) throws SQLException {
        Map<String, TabelaInfo> tabelas = new LinkedHashMap<>();
        String sql = """
                select table_schema, table_name
                from information_schema.tables
                where table_type = 'BASE TABLE'
                  and table_schema not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or table_schema = ?)
                order by table_schema, table_name
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(schemaFiltro));
            stmt.setString(2, valorFiltro(schemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String nome = rs.getString("table_name");
                tabelas.put(chaveTabela(schema, nome), new TabelaInfo(schema, nome));
            }
        }
        return tabelas;
    }

    private void carregarColunas(Connection conexao, String schemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select table_schema,
                       table_name,
                       column_name,
                       data_type,
                       udt_name,
                       character_maximum_length,
                       numeric_precision,
                       is_nullable
                from information_schema.columns
                where table_schema not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or table_schema = ?)
                order by table_schema, table_name, ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(schemaFiltro));
            stmt.setString(2, valorFiltro(schemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabela = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabela == null) {
                    continue;
                }

                String nome = rs.getString("column_name");
                tabela.colunas().put(nome, new ColunaInfo(
                        nome,
                        resolverTipo(rs.getString("data_type"), rs.getString("udt_name")),
                        resolverTamanho(rs.getObject("character_maximum_length"),
                                rs.getObject("numeric_precision")),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        tabela.primaryKeys().contains(nome)));
            }
        }
    }

    private void carregarPrimaryKeys(Connection conexao, String schemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select kcu.table_schema,
                       kcu.table_name,
                       kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                 and kcu.table_name = tc.table_name
                where tc.constraint_type = 'PRIMARY KEY'
                  and kcu.table_schema not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or kcu.table_schema = ?)
                order by kcu.table_schema, kcu.table_name, kcu.ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(schemaFiltro));
            stmt.setString(2, valorFiltro(schemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabela = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabela != null) {
                    tabela.primaryKeys().add(rs.getString("column_name"));
                }
            }
        }
    }

    @Override
    public void carregarForeignKeys(Connection conexao, String schemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select tc.table_schema,
                       tc.table_name,
                       tc.constraint_name,
                       kcu.column_name,
                       ccu.table_schema as ref_schema,
                       ccu.table_name as ref_table,
                       ccu.column_name as ref_column
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name
                 and kcu.table_schema = tc.table_schema
                 and kcu.table_name = tc.table_name
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.constraint_schema = tc.constraint_schema
                where tc.constraint_type = 'FOREIGN KEY'
                  and tc.table_schema not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or tc.table_schema = ?)
                order by tc.table_schema, tc.table_name, tc.constraint_name, kcu.ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(schemaFiltro));
            stmt.setString(2, valorFiltro(schemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabela = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabela == null) {
                    continue;
                }

                tabela.foreignKeys().add(new ForeignKeyInfo(
                        rs.getString("constraint_name"),
                        rs.getString("column_name"),
                        chaveTabela(rs.getString("ref_schema"), rs.getString("ref_table")),
                        rs.getString("ref_column")));
            }
        }
    }

    @Override
    public void carregarIndices(Connection conexao, String schemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select schemaname, tablename, indexname, indexdef
                from pg_indexes
                where schemaname not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or schemaname = ?)
                order by schemaname, tablename, indexname
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(schemaFiltro));
            stmt.setString(2, valorFiltro(schemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabela = tabelas.get(chaveTabela(rs.getString("schemaname"), rs.getString("tablename")));
                if (tabela == null) {
                    continue;
                }

                String indexDef = rs.getString("indexdef");
                tabela.indices().add(new IndiceInfo(
                        rs.getString("indexname"),
                        extrairColunasIndice(indexDef),
                        indexDef != null && indexDef.toUpperCase().contains("UNIQUE")));
            }
        }
    }

    private String valorFiltro(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private String resolverTipo(String dataType, String udtName) {
        if (dataType == null || dataType.isBlank()) {
            return udtName;
        }
        return "USER-DEFINED".equalsIgnoreCase(dataType) && udtName != null && !udtName.isBlank() ? udtName : dataType;
    }

    private Integer resolverTamanho(Object characterLength, Object numericPrecision) {
        Integer tamanho = toInteger(characterLength);
        if (tamanho != null && tamanho > 0) {
            return tamanho;
        }

        tamanho = toInteger(numericPrecision);
        return tamanho != null && tamanho > 0 ? tamanho : null;
    }

    private Integer toInteger(Object valor) {
        return valor instanceof Number number ? number.intValue() : null;
    }

    private List<String> extrairColunasIndice(String indexDef) {
        if (indexDef == null || indexDef.isBlank()) {
            return List.of();
        }

        int inicio = indexDef.indexOf('(');
        int fim = indexDef.lastIndexOf(')');
        if (inicio < 0 || fim <= inicio) {
            return List.of();
        }

        List<String> colunas = new ArrayList<>();
        for (String parte : indexDef.substring(inicio + 1, fim).split(",")) {
            String coluna = parte.trim();
            if (!coluna.isBlank()) {
                colunas.add(coluna);
            }
        }
        return colunas;
    }

    public static String chaveTabela(String schema, String tabela) {
        return schema + "." + tabela;
    }

    public record BancoSnapshot(Map<String, TabelaInfo> tabelas) {
    }

    public record TabelaInfo(
            String schema,
            String nome,
            Map<String, ColunaInfo> colunas,
            Set<String> primaryKeys,
            List<IndiceInfo> indices,
            List<ForeignKeyInfo> foreignKeys) {

        public TabelaInfo(String schema, String nome) {
            this(schema, nome, new LinkedHashMap<>(), new LinkedHashSet<>(), new ArrayList<>(), new ArrayList<>());
        }

        public String id() {
            return schema + "." + nome;
        }
    }

    public record ColunaInfo(String nome, String tipo, Integer tamanho, boolean nullable, boolean primaryKey) {
    }

    public record IndiceInfo(String nome, List<String> colunas, boolean unico) {

        public String assinatura() {
            return nome + "|" + unico + "|" + String.join(",", colunas);
        }
    }

    public record ForeignKeyInfo(String nome, String coluna, String tabelaReferencia, String colunaReferencia) {

        public String sourceTargetKey(String source) {
            return source + "|" + tabelaReferencia + "|" + nome;
        }

        public String assinatura() {
            return coluna + "->" + tabelaReferencia + "." + colunaReferencia;
        }
    }
}
