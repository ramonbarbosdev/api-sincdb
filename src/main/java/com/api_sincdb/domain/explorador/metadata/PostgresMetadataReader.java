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

    public long contarSchemas(Connection conexao) throws SQLException {
        String sql = """
                select count(*) as total
                from information_schema.schemata
                where schema_name not in ('pg_catalog', 'information_schema', 'pg_toast')
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong("total") : 0;
        }
    }

    public List<SchemaResumoInfo> listarSchemasResumo(Connection conexao) throws SQLException {
        String sql = """
                select s.schema_name,
                       count(t.table_name) as total_tabelas
                from information_schema.schemata s
                left join information_schema.tables t
                  on t.table_schema = s.schema_name
                 and t.table_type = 'BASE TABLE'
                where s.schema_name not in ('pg_catalog', 'information_schema', 'pg_toast')
                group by s.schema_name
                order by s.schema_name
                """;

        List<SchemaResumoInfo> schemas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                schemas.add(new SchemaResumoInfo(rs.getString("schema_name"), rs.getLong("total_tabelas")));
            }
        }
        return schemas;
    }

    public List<TabelaResumoInfo> listarTabelasResumo(Connection conexao, String schema) throws SQLException {
        String sql = """
                select t.table_name,
                       count(c.column_name) as total_colunas,
                       greatest(coalesce(cls.reltuples, 0), 0)::bigint as estimativa_registros
                from information_schema.tables t
                left join information_schema.columns c
                  on c.table_schema = t.table_schema
                 and c.table_name = t.table_name
                left join pg_namespace n
                  on n.nspname = t.table_schema
                left join pg_class cls
                  on cls.relnamespace = n.oid
                 and cls.relname = t.table_name
                where t.table_type = 'BASE TABLE'
                  and t.table_schema = ?
                group by t.table_name, cls.reltuples
                order by t.table_name
                """;

        List<TabelaResumoInfo> tabelas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tabelas.add(new TabelaResumoInfo(
                            rs.getString("table_name"),
                            rs.getInt("total_colunas"),
                            rs.getLong("estimativa_registros")));
                }
            }
        }
        return tabelas;
    }

    public List<TabelaGrafoInfo> listarTabelasGrafo(Connection conexao, String schema) throws SQLException {
        String sql = """
                select t.table_schema,
                       t.table_name,
                       count(distinct c.column_name) as total_colunas,
                       count(distinct tc.constraint_name) filter (where tc.constraint_type = 'FOREIGN KEY') as total_fks
                from information_schema.tables t
                left join information_schema.columns c
                  on c.table_schema = t.table_schema
                 and c.table_name = t.table_name
                left join information_schema.table_constraints tc
                  on tc.table_schema = t.table_schema
                 and tc.table_name = t.table_name
                where t.table_type = 'BASE TABLE'
                  and t.table_schema = ?
                group by t.table_schema, t.table_name
                order by t.table_name
                """;

        List<TabelaGrafoInfo> tabelas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String nome = rs.getString("table_name");
                    tabelas.add(new TabelaGrafoInfo(
                            chaveTabela(rs.getString("table_schema"), nome),
                            nome,
                            rs.getInt("total_colunas"),
                            rs.getInt("total_fks")));
                }
            }
        }
        return tabelas;
    }

    public List<GrafoFkInfo> listarForeignKeysGrafo(Connection conexao, String schema) throws SQLException {
        String sql = """
                select tc.table_schema,
                       tc.table_name,
                       ccu.table_schema as ref_schema,
                       ccu.table_name as ref_table
                from information_schema.table_constraints tc
                join information_schema.constraint_column_usage ccu
                  on ccu.constraint_name = tc.constraint_name
                 and ccu.constraint_schema = tc.constraint_schema
                where tc.constraint_type = 'FOREIGN KEY'
                  and tc.table_schema = ?
                order by tc.table_schema, tc.table_name
                """;

        List<GrafoFkInfo> fks = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    fks.add(new GrafoFkInfo(
                            chaveTabela(rs.getString("table_schema"), rs.getString("table_name")),
                            chaveTabela(rs.getString("ref_schema"), rs.getString("ref_table"))));
                }
            }
        }
        return fks;
    }

    public List<TabelaAssinaturaInfo> listarAssinaturasTabelas(Connection conexao, String schema) throws SQLException {
        String sql = """
                select t.table_schema,
                       t.table_name,
                       coalesce(cols.assinatura_colunas, '') as assinatura_colunas,
                       coalesce(idx.assinatura_indices, '') as assinatura_indices,
                       coalesce(fks.assinatura_fks, '') as assinatura_fks
                from information_schema.tables t
                left join lateral (
                    select string_agg(
                               c.column_name || ':' ||
                               coalesce(c.data_type, '') || ':' ||
                               coalesce(c.udt_name, '') || ':' ||
                               coalesce(c.character_maximum_length::text, '') || ':' ||
                               coalesce(c.numeric_precision::text, '') || ':' ||
                               coalesce(c.is_nullable, ''),
                               '|' order by c.ordinal_position
                           ) as assinatura_colunas
                    from information_schema.columns c
                    where c.table_schema = t.table_schema
                      and c.table_name = t.table_name
                ) cols on true
                left join lateral (
                    select string_agg(i.indexname || ':' || i.indexdef, '|' order by i.indexname) as assinatura_indices
                    from pg_indexes i
                    where i.schemaname = t.table_schema
                      and i.tablename = t.table_name
                ) idx on true
                left join lateral (
                    select string_agg(
                               tc.constraint_name || ':' || kcu.column_name || '->' ||
                               ccu.table_schema || '.' || ccu.table_name || '.' || ccu.column_name,
                               '|' order by tc.constraint_name, kcu.ordinal_position
                           ) as assinatura_fks
                    from information_schema.table_constraints tc
                    join information_schema.key_column_usage kcu
                      on kcu.constraint_name = tc.constraint_name
                     and kcu.table_schema = tc.table_schema
                     and kcu.table_name = tc.table_name
                    join information_schema.constraint_column_usage ccu
                      on ccu.constraint_name = tc.constraint_name
                     and ccu.constraint_schema = tc.constraint_schema
                    where tc.constraint_type = 'FOREIGN KEY'
                      and tc.table_schema = t.table_schema
                      and tc.table_name = t.table_name
                ) fks on true
                where t.table_type = 'BASE TABLE'
                  and t.table_schema = ?
                order by t.table_name
                """;

        List<TabelaAssinaturaInfo> tabelas = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tabelas.add(new TabelaAssinaturaInfo(
                            chaveTabela(rs.getString("table_schema"), rs.getString("table_name")),
                            rs.getString("assinatura_colunas"),
                            rs.getString("assinatura_indices"),
                            rs.getString("assinatura_fks")));
                }
            }
        }
        return tabelas;
    }

    public TabelaInfo carregarTabela(Connection conexao, String schema, String tabela, boolean incluirIndices,
            boolean incluirFks) throws SQLException {
        Map<String, TabelaInfo> tabelas = new LinkedHashMap<>();
        tabelas.put(chaveTabela(schema, tabela), new TabelaInfo(schema, tabela));

        if (!tabelaExiste(conexao, schema, tabela)) {
            return null;
        }

        carregarPrimaryKeysTabela(conexao, schema, tabela, tabelas);
        carregarColunasTabela(conexao, schema, tabela, tabelas);

        if (incluirFks) {
            carregarForeignKeysTabela(conexao, schema, tabela, tabelas);
        }

        if (incluirIndices) {
            carregarIndicesTabela(conexao, schema, tabela, tabelas);
        }

        return tabelas.get(chaveTabela(schema, tabela));
    }

    private boolean tabelaExiste(Connection conexao, String schema, String tabela) throws SQLException {
        String sql = """
                select 1
                from information_schema.tables
                where table_schema = ?
                  and table_name = ?
                  and table_type = 'BASE TABLE'
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tabela);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void carregarPrimaryKeysTabela(Connection conexao, String schema, String tabela,
            Map<String, TabelaInfo> tabelas) throws SQLException {
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
                  and kcu.table_schema = ?
                  and kcu.table_name = ?
                order by kcu.ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tabela);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabelaInfo = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabelaInfo != null) {
                    tabelaInfo.primaryKeys().add(rs.getString("column_name"));
                }
            }
        }
    }

    private void carregarColunasTabela(Connection conexao, String schema, String tabela,
            Map<String, TabelaInfo> tabelas) throws SQLException {
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
                where table_schema = ?
                  and table_name = ?
                order by ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tabela);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabelaInfo = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabelaInfo == null) {
                    continue;
                }

                String nome = rs.getString("column_name");
                tabelaInfo.colunas().put(nome, new ColunaInfo(
                        nome,
                        resolverTipo(rs.getString("data_type"), rs.getString("udt_name")),
                        resolverTamanho(rs.getObject("character_maximum_length"),
                                rs.getObject("numeric_precision")),
                        "YES".equalsIgnoreCase(rs.getString("is_nullable")),
                        tabelaInfo.primaryKeys().contains(nome)));
            }
        }
    }

    private void carregarForeignKeysTabela(Connection conexao, String schema, String tabela,
            Map<String, TabelaInfo> tabelas) throws SQLException {
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
                  and tc.table_schema = ?
                  and tc.table_name = ?
                order by tc.constraint_name, kcu.ordinal_position
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tabela);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabelaInfo = tabelas.get(chaveTabela(rs.getString("table_schema"), rs.getString("table_name")));
                if (tabelaInfo == null) {
                    continue;
                }

                tabelaInfo.foreignKeys().add(new ForeignKeyInfo(
                        rs.getString("constraint_name"),
                        rs.getString("column_name"),
                        chaveTabela(rs.getString("ref_schema"), rs.getString("ref_table")),
                        rs.getString("ref_column")));
            }
        }
    }

    private void carregarIndicesTabela(Connection conexao, String schema, String tabela,
            Map<String, TabelaInfo> tabelas) throws SQLException {
        String sql = """
                select schemaname, tablename, indexname, indexdef
                from pg_indexes
                where schemaname = ?
                  and tablename = ?
                order by indexname
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, schema);
            stmt.setString(2, tabela);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                TabelaInfo tabelaInfo = tabelas.get(chaveTabela(rs.getString("schemaname"), rs.getString("tablename")));
                if (tabelaInfo == null) {
                    continue;
                }

                String indexDef = rs.getString("indexdef");
                tabelaInfo.indices().add(new IndiceInfo(
                        rs.getString("indexname"),
                        extrairColunasIndice(indexDef),
                        indexDef != null && indexDef.toUpperCase().contains("UNIQUE")));
            }
        }
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

    public record SchemaResumoInfo(String nome, long totalTabelas) {
    }

    public record TabelaResumoInfo(String nome, int totalColunas, long estimativaRegistros) {
    }

    public record TabelaGrafoInfo(String id, String nome, int totalColunas, int totalFks) {
    }

    public record GrafoFkInfo(String source, String target) {
    }

    public record TabelaAssinaturaInfo(
            String id,
            String assinaturaColunas,
            String assinaturaIndices,
            String assinaturaFks) {

        public String assinaturaCompleta() {
            return assinaturaColunas + "#" + assinaturaIndices + "#" + assinaturaFks;
        }
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
