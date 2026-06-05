package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.stereotype.Service;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.ResumoComparacaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.ColunaDetalheDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.BancoSnapshot;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.ColunaInfo;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.ForeignKeyInfo;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.IndiceInfo;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.TabelaInfo;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorComparacaoService {

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;

    public ExploradorComparacaoService(ConexaoBanco conexaoBanco, PostgresMetadataReader metadataReader) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
    }

    ComparacaoBanco compararBanco(String token, String base, String schema, boolean incluirIndices, boolean incluirFks,
            String idConexao) throws Exception {
        CompletableFuture<BancoSnapshot> origemFuture = CompletableFuture.supplyAsync(
                () -> carregarSnapshot(token, base, schema, TipoConexao.CLOUD, incluirIndices, incluirFks, idConexao));
        CompletableFuture<BancoSnapshot> destinoFuture = CompletableFuture.supplyAsync(
                () -> carregarSnapshot(token, base, schema, TipoConexao.LOCAL, incluirIndices, incluirFks, idConexao));

        return new ComparacaoBanco(obterFuture(origemFuture), obterFuture(destinoFuture));
    }

    private BancoSnapshot carregarSnapshot(String token, String base, String schema, TipoConexao tipo,
            boolean incluirIndices, boolean incluirFks, String idConexao) {
        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            return metadataReader.carregarSnapshot(conexao, schema, incluirIndices, incluirFks);
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

    TableDiff compararTabela(TabelaInfo origem, TabelaInfo destino) {
        if (origem != null && destino == null) {
            List<String> sqlPreview = List.of(gerarCreateTable(origem));
            return new TableDiff("ausente_destino", origem.colunas().size(), origem.colunas().size(),
                    origem.indices().size(), origem.foreignKeys().size(), List.of("Tabela ausente no destino"),
                    colunasTabelaAusente(origem), sqlPreview);
        }

        if (origem == null && destino != null) {
            return new TableDiff("novo_destino", destino.colunas().size(), destino.colunas().size(),
                    destino.indices().size(), destino.foreignKeys().size(), List.of("Tabela existe apenas no destino"),
                    colunasTabelaNova(destino), List.of());
        }

        List<ColunaDetalheDTO> colunas = new ArrayList<>();
        List<String> observacoes = new ArrayList<>();
        List<String> sqlPreview = new ArrayList<>();
        long colunasDiferentes = 0;

        Set<String> nomesColunas = new LinkedHashSet<>();
        nomesColunas.addAll(origem.colunas().keySet());
        nomesColunas.addAll(destino.colunas().keySet());

        for (String nomeColuna : nomesColunas) {
            ColunaInfo colunaOrigem = origem.colunas().get(nomeColuna);
            ColunaInfo colunaDestino = destino.colunas().get(nomeColuna);

            if (colunaOrigem != null && colunaDestino == null) {
                colunasDiferentes++;
                observacoes.add("Coluna ausente no destino: " + nomeColuna);
                sqlPreview.add("ALTER TABLE " + origem.id() + " ADD COLUMN " + nomeColuna + " "
                        + tipoSql(colunaOrigem) + ";");
                colunas.add(new ColunaDetalheDTO(nomeColuna, tipoSql(colunaOrigem), null,
                        colunaOrigem.primaryKey(), false, "ausente_destino", "Coluna ausente no destino"));
            } else if (colunaOrigem == null && colunaDestino != null) {
                colunasDiferentes++;
                observacoes.add("Coluna existe apenas no destino: " + nomeColuna);
                colunas.add(new ColunaDetalheDTO(nomeColuna, null, tipoSql(colunaDestino),
                        false, colunaDestino.primaryKey(), "novo_destino", "Coluna existe apenas no destino"));
            } else if (colunasEquivalentes(colunaOrigem, colunaDestino)) {
                colunas.add(new ColunaDetalheDTO(nomeColuna, tipoSql(colunaOrigem), tipoSql(colunaDestino),
                        colunaOrigem.primaryKey(), colunaDestino.primaryKey(), "igual", null));
            } else {
                colunasDiferentes++;
                observacoes.add("Coluna diferente: " + nomeColuna);
                sqlPreview.add("-- Revisar alteracao de tipo");
                sqlPreview.add("ALTER TABLE " + origem.id() + " ALTER COLUMN " + nomeColuna + " TYPE "
                        + tipoSql(colunaOrigem) + ";");
                colunas.add(new ColunaDetalheDTO(nomeColuna, tipoSql(colunaOrigem), tipoSql(colunaDestino),
                        colunaOrigem.primaryKey(), colunaDestino.primaryKey(), "diferente",
                        "Tipo, tamanho, nulidade ou chave primaria diferente"));
            }
        }

        long indicesDiferentes = compararAssinaturas(
                origem.indices().stream().map(IndiceInfo::assinatura).toList(),
                destino.indices().stream().map(IndiceInfo::assinatura).toList());
        long fksDiferentes = compararAssinaturas(
                origem.foreignKeys().stream().map(ForeignKeyInfo::assinatura).toList(),
                destino.foreignKeys().stream().map(ForeignKeyInfo::assinatura).toList());

        long totalDiferencas = colunasDiferentes + indicesDiferentes + fksDiferentes;
        String status = totalDiferencas == 0 ? "igual" : "diferente";

        return new TableDiff(status, origem.colunas().size(), colunasDiferentes, indicesDiferentes, fksDiferentes,
                observacoes, colunas, sqlPreview);
    }

    ResumoComparacaoDTO resumir(Set<String> chaves, BancoSnapshot origem, BancoSnapshot destino) {
        long tabelasIguais = 0;
        long tabelasDiferentes = 0;
        long ausentesDestino = 0;
        long novasDestino = 0;
        long colunasDiferentes = 0;

        for (String chave : chaves) {
            TableDiff diff = compararTabela(origem.tabelas().get(chave), destino.tabelas().get(chave));
            colunasDiferentes += diff.colunasDiferentes();

            switch (diff.status()) {
                case "igual" -> tabelasIguais++;
                case "ausente_destino" -> ausentesDestino++;
                case "novo_destino" -> novasDestino++;
                default -> tabelasDiferentes++;
            }
        }

        return new ResumoComparacaoDTO(chaves.size(), tabelasIguais, tabelasDiferentes, ausentesDestino, novasDestino,
                colunasDiferentes);
    }

    Set<String> chavesPorSchema(ComparacaoBanco comparacao, String schema) {
        Set<String> chaves = new LinkedHashSet<>();
        comparacao.origem().tabelas().keySet().stream()
                .filter(chave -> pertenceAoSchema(chave, schema))
                .forEach(chaves::add);
        comparacao.destino().tabelas().keySet().stream()
                .filter(chave -> pertenceAoSchema(chave, schema))
                .forEach(chaves::add);
        return chaves;
    }

    List<String> schemas(ComparacaoBanco comparacao) {
        Set<String> schemas = new LinkedHashSet<>();
        comparacao.origem().tabelas().values().forEach(tabela -> schemas.add(tabela.schema()));
        comparacao.destino().tabelas().values().forEach(tabela -> schemas.add(tabela.schema()));
        return schemas.stream().sorted().toList();
    }

    private boolean pertenceAoSchema(String chave, String schema) {
        return chave != null && chave.startsWith(schema + ".");
    }

    private List<ColunaDetalheDTO> colunasTabelaAusente(TabelaInfo tabela) {
        return tabela.colunas().values().stream()
                .map(coluna -> new ColunaDetalheDTO(coluna.nome(), tipoSql(coluna), null, coluna.primaryKey(), false,
                        "ausente_destino", "Tabela ausente no destino"))
                .toList();
    }

    private List<ColunaDetalheDTO> colunasTabelaNova(TabelaInfo tabela) {
        return tabela.colunas().values().stream()
                .map(coluna -> new ColunaDetalheDTO(coluna.nome(), null, tipoSql(coluna), false, coluna.primaryKey(),
                        "novo_destino", "Tabela existe apenas no destino"))
                .toList();
    }

    private long compararAssinaturas(List<String> origem, List<String> destino) {
        Set<String> origemSet = new LinkedHashSet<>(origem);
        Set<String> destinoSet = new LinkedHashSet<>(destino);
        return origemSet.stream().filter(item -> !destinoSet.contains(item)).count()
                + destinoSet.stream().filter(item -> !origemSet.contains(item)).count();
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

    String tipoSql(ColunaInfo coluna) {
        if (coluna == null || coluna.tipo() == null) {
            return null;
        }

        String tipoLower = coluna.tipo().toLowerCase();
        if (coluna.tamanho() != null && coluna.tamanho() > 0
                && (tipoLower.contains("char") || tipoLower.contains("varchar"))) {
            return coluna.tipo() + "(" + coluna.tamanho() + ")";
        }
        return coluna.tipo();
    }

    String gerarCreateTable(TabelaInfo tabela) {
        List<String> definicoes = new ArrayList<>();
        tabela.colunas().values().forEach(coluna -> {
            String definicao = coluna.nome() + " " + tipoSql(coluna);
            if (!coluna.nullable()) {
                definicao += " NOT NULL";
            }
            definicoes.add(definicao);
        });

        if (!tabela.primaryKeys().isEmpty()) {
            definicoes.add("PRIMARY KEY (" + String.join(", ", tabela.primaryKeys()) + ")");
        }

        return "CREATE TABLE " + tabela.id() + " (\n    "
                + String.join(",\n    ", definicoes) + "\n);";
    }

    public record ComparacaoBanco(BancoSnapshot origem, BancoSnapshot destino) {
    }

    record TableDiff(
            String status,
            int totalColunas,
            long colunasDiferentes,
            long indicesDiferentes,
            long foreignKeysDiferentes,
            List<String> observacoes,
            List<ColunaDetalheDTO> colunas,
            List<String> sqlPreview) {

        long totalDiferencas() {
            return colunasDiferentes + indicesDiferentes + foreignKeysDiferentes;
        }
    }
}
