package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionException;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.AmbienteDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.ColunaComparacaoDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.ColunaDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.ComparacaoDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.ForeignKeyDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.IndiceDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.ResumoDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.SchemaDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.TabelaComparacaoDTO;
import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO.TabelaDTO;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorVisualService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final ConexaoBanco conexaoBanco;
    private final ConcurrentHashMap<String, CacheEntry> snapshotCache = new ConcurrentHashMap<>();

    public ExploradorVisualService(ConexaoBanco conexaoBanco) {
        this.conexaoBanco = conexaoBanco;
    }

    public ExploradorVisualResponseDTO comparar(String token, String base, String esquema) throws Exception {
        return comparar(token, base, esquema, true, true, false);
    }

    public ExploradorVisualResponseDTO comparar(
            String token,
            String base,
            String esquema,
            boolean incluirIndices,
            boolean incluirFks,
            boolean refresh) throws Exception {

        CompletableFuture<BancoSnapshot> origemFuture = CompletableFuture.supplyAsync(
                () -> carregarSnapshotComCache(token, base, esquema, TipoConexao.CLOUD, incluirIndices, incluirFks,
                        refresh));

        CompletableFuture<BancoSnapshot> destinoFuture = CompletableFuture.supplyAsync(
                () -> carregarSnapshotComCache(token, base, esquema, TipoConexao.LOCAL, incluirIndices, incluirFks,
                        refresh));

        BancoSnapshot origemSnapshot = obterFuture(origemFuture);
        BancoSnapshot destinoSnapshot = obterFuture(destinoFuture);

        ComparacaoResultado comparacao = compararSnapshots(origemSnapshot, destinoSnapshot);

        return new ExploradorVisualResponseDTO(
                base,
                esquema,
                LocalDateTime.now(),
                new AmbienteDTO("Producao Cloud", TipoConexao.CLOUD.name(), "conectado",
                        montarSchemas(origemSnapshot)),
                new AmbienteDTO("Homologacao Local", TipoConexao.LOCAL.name(), "conectado",
                        montarSchemas(destinoSnapshot)),
                new ComparacaoDTO(
                        comparacao.tabelas(),
                        comparacao.resumo(),
                        comparacao.sqlPreview()));
    }

    private BancoSnapshot carregarSnapshotComCache(
            String token,
            String base,
            String esquema,
            TipoConexao tipo,
            boolean incluirIndices,
            boolean incluirFks,
            boolean refresh) {
        String cacheKey = String.join("|",
                String.valueOf(token == null ? 0 : token.hashCode()),
                base,
                esquema,
                tipo.name(),
                String.valueOf(incluirIndices),
                String.valueOf(incluirFks));

        if (!refresh) {
            CacheEntry cacheEntry = snapshotCache.get(cacheKey);

            if (cacheEntry != null && !cacheEntry.expirado()) {
                return cacheEntry.snapshot();
            }
        }

        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token)) {
            BancoSnapshot snapshot = carregarSnapshot(conexao, esquema, incluirIndices, incluirFks);
            snapshotCache.put(cacheKey, new CacheEntry(snapshot, Instant.now().plus(CACHE_TTL)));
            return snapshot;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    private BancoSnapshot obterFuture(CompletableFuture<BancoSnapshot> future) throws Exception {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private BancoSnapshot carregarSnapshot(
            Connection conexao,
            String esquemaFiltro,
            boolean incluirIndices,
            boolean incluirFks) throws SQLException {
        Map<String, TabelaInfo> tabelas = new LinkedHashMap<>();

        carregarTabelas(conexao, esquemaFiltro, tabelas);
        carregarPrimaryKeys(conexao, esquemaFiltro, tabelas);
        carregarColunas(conexao, esquemaFiltro, tabelas);

        if (incluirFks) {
            carregarForeignKeys(conexao, esquemaFiltro, tabelas);
        }

        if (incluirIndices) {
            carregarIndices(conexao, esquemaFiltro, tabelas);
        }

        return new BancoSnapshot(tabelas);
    }

    private void carregarTabelas(Connection conexao, String esquemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select table_schema, table_name
                from information_schema.tables
                where table_type = 'BASE TABLE'
                  and table_schema not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or table_schema = ?)
                order by table_schema, table_name
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(esquemaFiltro));
            stmt.setString(2, valorFiltro(esquemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String nome = rs.getString("table_name");

                String chave = chaveTabela(schema, nome);
                tabelas.put(chave, new TabelaInfo(schema, nome));
            }
        }
    }

    private void carregarColunas(Connection conexao, String esquemaFiltro, Map<String, TabelaInfo> tabelas)
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
            stmt.setString(1, valorFiltro(esquemaFiltro));
            stmt.setString(2, valorFiltro(esquemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String schema = rs.getString("table_schema");
                String tabela = rs.getString("table_name");
                String chave = chaveTabela(schema, tabela);
                TabelaInfo tabelaInfo = tabelas.get(chave);

                if (tabelaInfo == null) {
                    continue;
                }

                String nome = rs.getString("column_name");
                String tipo = resolverTipo(rs.getString("data_type"), rs.getString("udt_name"));
                Integer tamanho = resolverTamanho(rs.getObject("character_maximum_length"),
                        rs.getObject("numeric_precision"));
                boolean nullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));

                tabelaInfo.colunas().put(nome,
                        new ColunaInfo(nome, tipo, tamanho, nullable, tabelaInfo.primaryKeys().contains(nome)));
            }
        }
    }

    private void carregarPrimaryKeys(Connection conexao, String esquemaFiltro, Map<String, TabelaInfo> tabelas)
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
            stmt.setString(1, valorFiltro(esquemaFiltro));
            stmt.setString(2, valorFiltro(esquemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String chave = chaveTabela(rs.getString("table_schema"), rs.getString("table_name"));
                TabelaInfo tabela = tabelas.get(chave);

                if (tabela != null) {
                    tabela.primaryKeys().add(rs.getString("column_name"));
                }
            }
        }
    }

    private void carregarForeignKeys(Connection conexao, String esquemaFiltro, Map<String, TabelaInfo> tabelas)
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
            stmt.setString(1, valorFiltro(esquemaFiltro));
            stmt.setString(2, valorFiltro(esquemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String chave = chaveTabela(rs.getString("table_schema"), rs.getString("table_name"));
                TabelaInfo tabela = tabelas.get(chave);

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

    private void carregarIndices(Connection conexao, String esquemaFiltro, Map<String, TabelaInfo> tabelas)
            throws SQLException {
        String sql = """
                select schemaname, tablename, indexname, indexdef
                from pg_indexes
                where schemaname not in ('pg_catalog', 'information_schema', 'pg_toast')
                  and (? is null or schemaname = ?)
                order by schemaname, tablename, indexname
                """;

        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setString(1, valorFiltro(esquemaFiltro));
            stmt.setString(2, valorFiltro(esquemaFiltro));

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String chave = chaveTabela(rs.getString("schemaname"), rs.getString("tablename"));
                TabelaInfo tabela = tabelas.get(chave);

                if (tabela == null) {
                    continue;
                }

                String nome = rs.getString("indexname");
                String indexDef = rs.getString("indexdef");
                tabela.indices().add(new IndiceInfo(nome, extrairColunasIndice(indexDef),
                        indexDef != null && indexDef.toUpperCase().contains("UNIQUE")));
            }
        }
    }

    private ComparacaoResultado compararSnapshots(BancoSnapshot origem, BancoSnapshot destino) {
        Set<String> chaves = new LinkedHashSet<>();
        chaves.addAll(origem.tabelas().keySet());
        chaves.addAll(destino.tabelas().keySet());

        List<TabelaComparacaoDTO> tabelasComparadas = new ArrayList<>();
        List<String> sqlPreview = new ArrayList<>();

        long tabelasIguais = 0;
        long tabelasDiferentes = 0;
        long tabelasAusentesDestino = 0;
        long tabelasNovasDestino = 0;
        long colunasIguais = 0;
        long colunasDiferentes = 0;
        long colunasAusentesDestino = 0;
        long colunasNovasDestino = 0;
        long indicesDiferentes = 0;
        long foreignKeysDiferentes = 0;

        for (String chave : chaves) {
            TabelaInfo tabelaOrigem = origem.tabelas().get(chave);
            TabelaInfo tabelaDestino = destino.tabelas().get(chave);

            if (tabelaOrigem != null && tabelaDestino == null) {
                tabelasAusentesDestino++;
                tabelasComparadas.add(montarTabelaAusenteDestino(tabelaOrigem));
                sqlPreview.add(gerarCreateTable(tabelaOrigem));
                continue;
            }

            if (tabelaOrigem == null && tabelaDestino != null) {
                tabelasNovasDestino++;
                tabelasComparadas.add(montarTabelaNovaDestino(tabelaDestino));
                continue;
            }

            ComparacaoTabela comparacao = compararTabela(tabelaOrigem, tabelaDestino, sqlPreview);
            colunasIguais += comparacao.colunasIguais();
            colunasDiferentes += comparacao.colunasDiferentes();
            colunasAusentesDestino += comparacao.colunasAusentesDestino();
            colunasNovasDestino += comparacao.colunasNovasDestino();
            indicesDiferentes += comparacao.indicesDiferentes();
            foreignKeysDiferentes += comparacao.foreignKeysDiferentes();

            if ("igual".equals(comparacao.tabela().status())) {
                tabelasIguais++;
            } else {
                tabelasDiferentes++;
            }

            tabelasComparadas.add(comparacao.tabela());
        }

        tabelasComparadas.sort(Comparator.comparing(TabelaComparacaoDTO::nomeCompleto));

        ResumoDTO resumo = new ResumoDTO(
                tabelasIguais,
                tabelasDiferentes,
                tabelasAusentesDestino,
                tabelasNovasDestino,
                colunasIguais,
                colunasDiferentes,
                colunasAusentesDestino,
                colunasNovasDestino,
                indicesDiferentes,
                foreignKeysDiferentes);

        return new ComparacaoResultado(tabelasComparadas, resumo, sqlPreview);
    }

    private ComparacaoTabela compararTabela(TabelaInfo origem, TabelaInfo destino, List<String> sqlPreview) {
        Set<String> nomesColunas = new LinkedHashSet<>();
        nomesColunas.addAll(origem.colunas().keySet());
        nomesColunas.addAll(destino.colunas().keySet());

        List<ColunaComparacaoDTO> colunas = new ArrayList<>();
        List<String> observacoes = new ArrayList<>();

        long iguais = 0;
        long diferentes = 0;
        long ausentesDestino = 0;
        long novasDestino = 0;

        for (String nomeColuna : nomesColunas) {
            ColunaInfo colunaOrigem = origem.colunas().get(nomeColuna);
            ColunaInfo colunaDestino = destino.colunas().get(nomeColuna);

            if (colunaOrigem != null && colunaDestino == null) {
                ausentesDestino++;
                observacoes.add("Coluna ausente no destino: " + nomeColuna);
                sqlPreview.add("ALTER TABLE " + origem.nomeCompleto() + " ADD COLUMN "
                        + nomeColuna + " " + tipoSql(colunaOrigem) + ";");
                colunas.add(new ColunaComparacaoDTO(
                        nomeColuna,
                        tipoFormatado(colunaOrigem),
                        null,
                        colunaOrigem.primaryKey(),
                        false,
                        "ausente_destino",
                        "Coluna ausente no destino"));
                continue;
            }

            if (colunaOrigem == null && colunaDestino != null) {
                novasDestino++;
                observacoes.add("Coluna existe apenas no destino: " + nomeColuna);
                colunas.add(new ColunaComparacaoDTO(
                        nomeColuna,
                        null,
                        tipoFormatado(colunaDestino),
                        false,
                        colunaDestino.primaryKey(),
                        "novo_destino",
                        "Coluna existe apenas no destino"));
                continue;
            }

            if (colunasEquivalentes(colunaOrigem, colunaDestino)) {
                iguais++;
                colunas.add(new ColunaComparacaoDTO(
                        nomeColuna,
                        tipoFormatado(colunaOrigem),
                        tipoFormatado(colunaDestino),
                        colunaOrigem.primaryKey(),
                        colunaDestino.primaryKey(),
                        "igual",
                        null));
            } else {
                diferentes++;
                observacoes.add("Coluna diferente: " + nomeColuna);
                sqlPreview.add("-- Revisar alteracao de tipo");
                sqlPreview.add("ALTER TABLE " + origem.nomeCompleto() + " ALTER COLUMN "
                        + nomeColuna + " TYPE " + tipoSql(colunaOrigem) + ";");
                colunas.add(new ColunaComparacaoDTO(
                        nomeColuna,
                        tipoFormatado(colunaOrigem),
                        tipoFormatado(colunaDestino),
                        colunaOrigem.primaryKey(),
                        colunaDestino.primaryKey(),
                        "diferente",
                        "Tipo, tamanho, nulidade ou chave primaria diferente"));
            }
        }

        long indicesDiferentes = compararAssinaturas(
                origem.indices().stream().map(IndiceInfo::assinatura).toList(),
                destino.indices().stream().map(IndiceInfo::assinatura).toList());

        long fksDiferentes = compararAssinaturas(
                origem.foreignKeys().stream().map(ForeignKeyInfo::assinatura).toList(),
                destino.foreignKeys().stream().map(ForeignKeyInfo::assinatura).toList());

        String status = (diferentes + ausentesDestino + novasDestino + indicesDiferentes + fksDiferentes) == 0
                ? "igual"
                : "diferente";

        TabelaComparacaoDTO tabela = new TabelaComparacaoDTO(
                origem.schema(),
                origem.nome(),
                origem.nomeCompleto(),
                status,
                colunas,
                origem.indices().stream().map(indice -> toIndiceDTO(indice, "comparado")).toList(),
                origem.foreignKeys().stream().map(fk -> toForeignKeyDTO(fk, "comparado")).toList(),
                observacoes);

        return new ComparacaoTabela(
                tabela,
                iguais,
                diferentes,
                ausentesDestino,
                novasDestino,
                indicesDiferentes,
                fksDiferentes);
    }

    private TabelaComparacaoDTO montarTabelaAusenteDestino(TabelaInfo tabela) {
        return new TabelaComparacaoDTO(
                tabela.schema(),
                tabela.nome(),
                tabela.nomeCompleto(),
                "ausente_destino",
                tabela.colunas().values().stream()
                        .map(coluna -> new ColunaComparacaoDTO(
                                coluna.nome(),
                                tipoFormatado(coluna),
                                null,
                                coluna.primaryKey(),
                                false,
                                "ausente_destino",
                                "Tabela ausente no destino"))
                        .toList(),
                tabela.indices().stream().map(indice -> toIndiceDTO(indice, "ausente_destino")).toList(),
                tabela.foreignKeys().stream().map(fk -> toForeignKeyDTO(fk, "ausente_destino")).toList(),
                List.of("Tabela ausente no destino"));
    }

    private TabelaComparacaoDTO montarTabelaNovaDestino(TabelaInfo tabela) {
        return new TabelaComparacaoDTO(
                tabela.schema(),
                tabela.nome(),
                tabela.nomeCompleto(),
                "novo_destino",
                tabela.colunas().values().stream()
                        .map(coluna -> new ColunaComparacaoDTO(
                                coluna.nome(),
                                null,
                                tipoFormatado(coluna),
                                false,
                                coluna.primaryKey(),
                                "novo_destino",
                                "Tabela existe apenas no destino"))
                        .toList(),
                tabela.indices().stream().map(indice -> toIndiceDTO(indice, "novo_destino")).toList(),
                tabela.foreignKeys().stream().map(fk -> toForeignKeyDTO(fk, "novo_destino")).toList(),
                List.of("Tabela existe apenas no destino"));
    }

    private List<SchemaDTO> montarSchemas(BancoSnapshot snapshot) {
        Map<String, List<TabelaDTO>> schemas = new LinkedHashMap<>();

        snapshot.tabelas().values().forEach(tabela -> schemas
                .computeIfAbsent(tabela.schema(), key -> new ArrayList<>())
                .add(toTabelaDTO(tabela, "carregado")));

        return schemas.entrySet().stream()
                .map(entry -> new SchemaDTO(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(TabelaDTO::nome))
                                .toList()))
                .sorted(Comparator.comparing(SchemaDTO::nome))
                .toList();
    }

    private TabelaDTO toTabelaDTO(TabelaInfo tabela, String status) {
        return new TabelaDTO(
                tabela.schema(),
                tabela.nome(),
                tabela.nomeCompleto(),
                status,
                tabela.colunas().values().stream()
                        .map(coluna -> new ColunaDTO(
                                coluna.nome(),
                                tipoFormatado(coluna),
                                coluna.tamanho(),
                                coluna.nullable(),
                                coluna.primaryKey(),
                                status))
                        .toList(),
                tabela.indices().stream().map(indice -> toIndiceDTO(indice, status)).toList(),
                tabela.foreignKeys().stream().map(fk -> toForeignKeyDTO(fk, status)).toList());
    }

    private IndiceDTO toIndiceDTO(IndiceInfo indice, String status) {
        return new IndiceDTO(indice.nome(), indice.colunas(), indice.unico(), status);
    }

    private ForeignKeyDTO toForeignKeyDTO(ForeignKeyInfo fk, String status) {
        return new ForeignKeyDTO(fk.nome(), fk.coluna(), fk.tabelaReferencia(), fk.colunaReferencia(), status);
    }

    private String gerarCreateTable(TabelaInfo tabela) {
        StringJoiner joiner = new StringJoiner(",\n    ");

        tabela.colunas().values().forEach(coluna -> {
            String definicao = coluna.nome() + " " + tipoSql(coluna);
            if (!coluna.nullable()) {
                definicao += " NOT NULL";
            }
            joiner.add(definicao);
        });

        if (!tabela.primaryKeys().isEmpty()) {
            joiner.add("PRIMARY KEY (" + String.join(", ", tabela.primaryKeys()) + ")");
        }

        return "CREATE TABLE " + tabela.nomeCompleto() + " (\n    " + joiner + "\n);";
    }

    private String tipoFormatado(ColunaInfo coluna) {
        if (coluna == null) {
            return null;
        }

        return tipoSql(coluna);
    }

    private String tipoSql(ColunaInfo coluna) {
        String tipo = coluna.tipo();

        if (tipo == null) {
            return "";
        }

        String tipoLower = tipo.toLowerCase();

        if (coluna.tamanho() != null && coluna.tamanho() > 0
                && (tipoLower.contains("char") || tipoLower.contains("varchar"))) {
            return tipo + "(" + coluna.tamanho() + ")";
        }

        return tipo;
    }

    private boolean colunasEquivalentes(ColunaInfo origem, ColunaInfo destino) {
        return origem != null
                && destino != null
                && Objects.equals(normalizarTipo(origem.tipo()), normalizarTipo(destino.tipo()))
                && Objects.equals(origem.tamanho(), destino.tamanho())
                && origem.nullable() == destino.nullable()
                && origem.primaryKey() == destino.primaryKey();
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null) {
            return null;
        }

        return tipo.toLowerCase()
                .replace("character varying", "varchar")
                .replace("int4", "integer")
                .replace("int8", "bigint");
    }

    private long compararAssinaturas(List<String> origem, List<String> destino) {
        Set<String> origemSet = new LinkedHashSet<>(origem);
        Set<String> destinoSet = new LinkedHashSet<>(destino);

        long diferencasOrigem = origemSet.stream().filter(item -> !destinoSet.contains(item)).count();
        long diferencasDestino = destinoSet.stream().filter(item -> !origemSet.contains(item)).count();

        return diferencasOrigem + diferencasDestino;
    }

    private boolean schemaIgnorado(String schema) {
        return schema == null
                || "pg_catalog".equals(schema)
                || "information_schema".equals(schema)
                || "pg_toast".equals(schema);
    }

    private String valorFiltro(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private String resolverTipo(String dataType, String udtName) {
        if (dataType == null || dataType.isBlank()) {
            return udtName;
        }

        if ("USER-DEFINED".equalsIgnoreCase(dataType) && udtName != null && !udtName.isBlank()) {
            return udtName;
        }

        return dataType;
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
        if (valor instanceof Number number) {
            return number.intValue();
        }

        return null;
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

        String conteudo = indexDef.substring(inicio + 1, fim);
        List<String> colunas = new ArrayList<>();

        for (String parte : conteudo.split(",")) {
            String coluna = parte.trim();
            if (!coluna.isBlank()) {
                colunas.add(coluna);
            }
        }

        return colunas;
    }

    private String chaveTabela(String schema, String tabela) {
        return schema + "." + tabela;
    }

    private record BancoSnapshot(Map<String, TabelaInfo> tabelas) {
    }

    private record TabelaInfo(
            String schema,
            String nome,
            Map<String, ColunaInfo> colunas,
            Set<String> primaryKeys,
            List<IndiceInfo> indices,
            List<ForeignKeyInfo> foreignKeys) {

        TabelaInfo(String schema, String nome) {
            this(schema, nome, new LinkedHashMap<>(), new LinkedHashSet<>(), new ArrayList<>(), new ArrayList<>());
        }

        String nomeCompleto() {
            return schema + "." + nome;
        }
    }

    private record ColunaInfo(
            String nome,
            String tipo,
            Integer tamanho,
            boolean nullable,
            boolean primaryKey) {
    }

    private record ForeignKeyInfo(
            String nome,
            String coluna,
            String tabelaReferencia,
            String colunaReferencia) {

        String assinatura() {
            return coluna + "->" + tabelaReferencia + "." + colunaReferencia;
        }
    }

    private record IndiceInfo(
            String nome,
            List<String> colunas,
            boolean unico) {

        String assinatura() {
            return nome + "|" + unico + "|" + String.join(",", colunas);
        }
    }

    private record IndiceBuilder(
            String nome,
            boolean unico,
            List<String> colunas) {

        IndiceBuilder(String nome, boolean unico) {
            this(nome, unico, new ArrayList<>());
        }
    }

    private record CacheEntry(BancoSnapshot snapshot, Instant expiresAt) {

        boolean expirado() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private record ComparacaoResultado(
            List<TabelaComparacaoDTO> tabelas,
            ResumoDTO resumo,
            List<String> sqlPreview) {
    }

    private record ComparacaoTabela(
            TabelaComparacaoDTO tabela,
            long colunasIguais,
            long colunasDiferentes,
            long colunasAusentesDestino,
            long colunasNovasDestino,
            long indicesDiferentes,
            long foreignKeysDiferentes) {
    }
}
