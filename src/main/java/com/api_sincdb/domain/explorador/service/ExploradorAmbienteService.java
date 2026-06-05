package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.DadosTabelaDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.AcaoTabelaDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.ColunaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.ForeignKeyExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.IndiceExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaResumoDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.BancoSnapshot;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.TabelaInfo;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorAmbienteService {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 500;

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;

    public ExploradorAmbienteService(ConexaoBanco conexaoBanco, PostgresMetadataReader metadataReader) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
    }

    public List<String> listarBases(String token, String ambiente, String idConexao) throws Exception {
        TipoConexao tipo = resolverAmbiente(ambiente);
        try (Connection conexao = conexaoBanco.abrirConexao("mudar", tipo, token, idConexao)) {
            return metadataReader.listarBases(conexao);
        }
    }

    public List<String> listarSchemas(String token, String ambiente, String base, String idConexao) throws Exception {
        TipoConexao tipo = resolverAmbiente(ambiente);
        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            return metadataReader.listarSchemas(conexao);
        }
    }

    public List<TabelaResumoDTO> listarTabelas(String token, String ambiente, String base, String schema,
            String idConexao) throws Exception {
        BancoSnapshot snapshot = carregarSnapshot(token, ambiente, base, schema, idConexao);
        return snapshot.tabelas().values().stream()
                .map(tabela -> new TabelaResumoDTO(tabela.id(), tabela.schema(), tabela.nome(),
                        tabela.colunas().size(), tabela.indices().size(), tabela.foreignKeys().size()))
                .sorted(Comparator.comparing(TabelaResumoDTO::nome))
                .toList();
    }

    public TabelaExploracaoDTO buscarTabela(String token, String ambiente, String base, String schema, String tabela,
            String idConexao) throws Exception {
        BancoSnapshot snapshot = carregarSnapshot(token, ambiente, base, schema, idConexao);
        TabelaInfo tabelaInfo = obterTabela(snapshot, schema, tabela);

        return new TabelaExploracaoDTO(
                ambiente.toLowerCase(),
                base,
                tabelaInfo.id(),
                tabelaInfo.schema(),
                tabelaInfo.nome(),
                tabelaInfo.colunas().size(),
                tabelaInfo.indices().size(),
                tabelaInfo.foreignKeys().size(),
                tabelaInfo.colunas().values().stream()
                        .map(coluna -> new ColunaExploracaoDTO(coluna.nome(), tipoSql(coluna.tipo(), coluna.tamanho()),
                                coluna.tamanho(), coluna.nullable(), coluna.primaryKey()))
                        .toList(),
                tabelaInfo.indices().stream()
                        .map(indice -> new IndiceExploracaoDTO(indice.nome(), indice.colunas(), indice.unico()))
                        .sorted(Comparator.comparing(IndiceExploracaoDTO::nome))
                        .toList(),
                tabelaInfo.foreignKeys().stream()
                        .map(fk -> new ForeignKeyExploracaoDTO(fk.nome(), fk.coluna(), fk.tabelaReferencia(),
                                fk.colunaReferencia()))
                        .sorted(Comparator.comparing(ForeignKeyExploracaoDTO::nome))
                        .toList(),
                acoesDisponiveis(),
                gerarSelectPreview(tabelaInfo));
    }

    public DadosTabelaDTO previewDados(String token, String ambiente, String base, String schema, String tabela,
            int limit, String idConexao) throws Exception {
        int limiteSeguro = normalizarLimit(limit);
        TipoConexao tipo = resolverAmbiente(ambiente);

        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            validarTabelaExiste(conexao, schema, tabela);
            String sql = "select * from " + quoteIdent(schema) + "." + quoteIdent(tabela) + " limit ?";

            try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
                stmt.setInt(1, limiteSeguro);
                try (ResultSet rs = stmt.executeQuery()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    List<String> colunas = new ArrayList<>();

                    for (int i = 1; i <= metaData.getColumnCount(); i++) {
                        colunas.add(metaData.getColumnLabel(i));
                    }

                    List<Map<String, Object>> registros = new ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> registro = new LinkedHashMap<>();
                        for (String coluna : colunas) {
                            registro.put(coluna, rs.getObject(coluna));
                        }
                        registros.add(registro);
                    }

                    return new DadosTabelaDTO(ambiente.toLowerCase(), base, schema, tabela, limiteSeguro, colunas,
                            registros);
                }
            }
        }
    }

    private BancoSnapshot carregarSnapshot(String token, String ambiente, String base, String schema, String idConexao)
            throws Exception {
        TipoConexao tipo = resolverAmbiente(ambiente);
        try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
            return metadataReader.carregarSnapshot(conexao, schema, true, true);
        }
    }

    private TipoConexao resolverAmbiente(String ambiente) {
        if ("cloud".equalsIgnoreCase(ambiente)) {
            return TipoConexao.CLOUD;
        }
        if ("local".equalsIgnoreCase(ambiente)) {
            return TipoConexao.LOCAL;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente deve ser cloud ou local");
    }

    private TabelaInfo obterTabela(BancoSnapshot snapshot, String schema, String tabela) {
        TabelaInfo tabelaInfo = snapshot.tabelas().get(schema + "." + tabela);
        if (tabelaInfo == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela nao encontrada");
        }
        return tabelaInfo;
    }

    private void validarTabelaExiste(Connection conexao, String schema, String tabela) throws SQLException {
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
                if (!rs.next()) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela nao encontrada");
                }
            }
        }
    }

    private int normalizarLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<AcaoTabelaDTO> acoesDisponiveis() {
        return List.of(
                new AcaoTabelaDTO("visualizar_estrutura", "Visualizar estrutura", true),
                new AcaoTabelaDTO("visualizar_registros", "Visualizar registros", true),
                new AcaoTabelaDTO("executar_select", "Executar SELECT", false),
                new AcaoTabelaDTO("gerar_sql", "Gerar SQL", true),
                new AcaoTabelaDTO("exportar_dados", "Exportar dados", false),
                new AcaoTabelaDTO("sincronizar_tabela", "Sincronizar tabela", false),
                new AcaoTabelaDTO("copiar_estrutura", "Copiar estrutura", false),
                new AcaoTabelaDTO("comparar_tabela", "Comparar tabela com outro ambiente", false));
    }

    private String gerarSelectPreview(TabelaInfo tabela) {
        return "select * from " + quoteIdent(tabela.schema()) + "." + quoteIdent(tabela.nome()) + " limit 100;";
    }

    private String tipoSql(String tipo, Integer tamanho) {
        if (tipo == null) {
            return null;
        }
        String tipoLower = tipo.toLowerCase();
        if (tamanho != null && tamanho > 0 && (tipoLower.contains("char") || tipoLower.contains("varchar"))) {
            return tipo + "(" + tamanho + ")";
        }
        return tipo;
    }

    private String quoteIdent(String valor) {
        return "\"" + valor.replace("\"", "\"\"") + "\"";
    }
}
