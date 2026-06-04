package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private final ConexaoBanco conexaoBanco;

    public ExploradorVisualService(ConexaoBanco conexaoBanco) {
        this.conexaoBanco = conexaoBanco;
    }

    public ExploradorVisualResponseDTO comparar(String token, String base, String esquema) throws Exception {
        try (Connection origem = conexaoBanco.abrirConexao(base, TipoConexao.CLOUD, token);
                Connection destino = conexaoBanco.abrirConexao(base, TipoConexao.LOCAL, token)) {

            BancoSnapshot origemSnapshot = carregarSnapshot(origem, esquema);
            BancoSnapshot destinoSnapshot = carregarSnapshot(destino, esquema);

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
    }

    private BancoSnapshot carregarSnapshot(Connection conexao, String esquemaFiltro) throws SQLException {
        DatabaseMetaData metaData = conexao.getMetaData();
        Map<String, TabelaInfo> tabelas = new LinkedHashMap<>();

        try (ResultSet rs = metaData.getTables(null, esquemaFiltro, "%", new String[] { "TABLE" })) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String nome = rs.getString("TABLE_NAME");

                if (schemaIgnorado(schema)) {
                    continue;
                }

                String chave = chaveTabela(schema, nome);
                TabelaInfo tabela = new TabelaInfo(schema, nome);
                tabela.primaryKeys().addAll(carregarPrimaryKeys(metaData, schema, nome));
                tabela.colunas().putAll(carregarColunas(metaData, schema, nome, tabela.primaryKeys()));
                tabela.foreignKeys().addAll(carregarForeignKeys(metaData, schema, nome));
                tabela.indices().addAll(carregarIndices(metaData, schema, nome));
                tabelas.put(chave, tabela);
            }
        }

        return new BancoSnapshot(tabelas);
    }

    private Map<String, ColunaInfo> carregarColunas(DatabaseMetaData metaData, String schema, String tabela,
            Set<String> primaryKeys) throws SQLException {
        Map<String, ColunaInfo> colunas = new LinkedHashMap<>();

        try (ResultSet rs = metaData.getColumns(null, schema, tabela, "%")) {
            while (rs.next()) {
                String nome = rs.getString("COLUMN_NAME");
                String tipo = rs.getString("TYPE_NAME");
                int tamanho = rs.getInt("COLUMN_SIZE");
                boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;

                colunas.put(nome, new ColunaInfo(nome, tipo, tamanho, nullable, primaryKeys.contains(nome)));
            }
        }

        return colunas;
    }

    private Set<String> carregarPrimaryKeys(DatabaseMetaData metaData, String schema, String tabela) throws SQLException {
        Set<String> primaryKeys = new LinkedHashSet<>();

        try (ResultSet rs = metaData.getPrimaryKeys(null, schema, tabela)) {
            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }

        return primaryKeys;
    }

    private List<ForeignKeyInfo> carregarForeignKeys(DatabaseMetaData metaData, String schema, String tabela)
            throws SQLException {
        List<ForeignKeyInfo> fks = new ArrayList<>();

        try (ResultSet rs = metaData.getImportedKeys(null, schema, tabela)) {
            while (rs.next()) {
                String nome = rs.getString("FK_NAME");
                String coluna = rs.getString("FKCOLUMN_NAME");
                String schemaReferencia = rs.getString("PKTABLE_SCHEM");
                String tabelaReferencia = rs.getString("PKTABLE_NAME");
                String colunaReferencia = rs.getString("PKCOLUMN_NAME");

                fks.add(new ForeignKeyInfo(
                        nome,
                        coluna,
                        chaveTabela(schemaReferencia, tabelaReferencia),
                        colunaReferencia));
            }
        }

        return fks;
    }

    private List<IndiceInfo> carregarIndices(DatabaseMetaData metaData, String schema, String tabela) throws SQLException {
        Map<String, IndiceBuilder> builders = new LinkedHashMap<>();

        try (ResultSet rs = metaData.getIndexInfo(null, schema, tabela, false, false)) {
            while (rs.next()) {
                short tipo = rs.getShort("TYPE");
                String nome = rs.getString("INDEX_NAME");
                String coluna = rs.getString("COLUMN_NAME");

                if (tipo == DatabaseMetaData.tableIndexStatistic || nome == null || coluna == null) {
                    continue;
                }

                boolean unico = !rs.getBoolean("NON_UNIQUE");
                builders.computeIfAbsent(nome, key -> new IndiceBuilder(nome, unico)).colunas().add(coluna);
            }
        }

        return builders.values().stream()
                .map(builder -> new IndiceInfo(builder.nome(), builder.colunas(), builder.unico()))
                .toList();
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
