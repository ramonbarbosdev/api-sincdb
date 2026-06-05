package com.api_sincdb.domain.explorador.service;

import java.sql.Connection;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.config.ConexaoBanco;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.AcaoTabelaDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.ColunaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.ForeignKeyExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO.IndiceExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaListaResponseDTO;
import com.api_sincdb.domain.explorador.dto.TabelaListaResponseDTO.TabelaItemDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.TabelaInfo;
import com.api_sincdb.enums.TipoConexao;

@Service
public class ExploradorTabelaLazyService {

    private final ConexaoBanco conexaoBanco;
    private final PostgresMetadataReader metadataReader;
    private final ExploradorMetadataCacheService cacheService;
    private final ExploradorAmbienteResolver ambienteResolver;

    public ExploradorTabelaLazyService(
            ConexaoBanco conexaoBanco,
            PostgresMetadataReader metadataReader,
            ExploradorMetadataCacheService cacheService,
            ExploradorAmbienteResolver ambienteResolver) {
        this.conexaoBanco = conexaoBanco;
        this.metadataReader = metadataReader;
        this.cacheService = cacheService;
        this.ambienteResolver = ambienteResolver;
    }

    public TabelaListaResponseDTO listarTabelas(String token, String ambiente, String base, String schema,
            String idConexao) {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        String cacheKey = cacheKey(token, idConexao, ambiente, base, schema, "tabelas");

        return cacheService.get(cacheKey, () -> unchecked(() -> {
            try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
                return new TabelaListaResponseDTO(metadataReader.listarTabelasResumo(conexao, schema).stream()
                        .map(tabela -> new TabelaItemDTO(tabela.nome(), tabela.totalColunas(),
                                tabela.estimativaRegistros()))
                        .toList());
            }
        }));
    }

    public TabelaExploracaoDTO detalharTabela(String token, String ambiente, String base, String schema,
            String tabela, String idConexao) {
        TipoConexao tipo = ambienteResolver.resolver(ambiente);
        String cacheKey = cacheKey(token, idConexao, ambiente, base, schema, "tabela|" + tabela);

        return cacheService.get(cacheKey, () -> unchecked(() -> {
            try (Connection conexao = conexaoBanco.abrirConexao(base, tipo, token, idConexao)) {
                TabelaInfo tabelaInfo = metadataReader.carregarTabela(conexao, schema, tabela, true, true);

                if (tabelaInfo == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela nao encontrada");
                }

                return montarDetalhe(ambiente, base, tabelaInfo);
            }
        }));
    }

    private TabelaExploracaoDTO montarDetalhe(String ambiente, String base, TabelaInfo tabelaInfo) {
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
                "");
    }

    private List<AcaoTabelaDTO> acoesDisponiveis() {
        return List.of(
                new AcaoTabelaDTO("visualizar_estrutura", "Visualizar estrutura", true),
                new AcaoTabelaDTO("visualizar_registros", "Visualizar registros", true),
                new AcaoTabelaDTO("executar_select", "Executar SELECT", false),
                new AcaoTabelaDTO("gerar_sql", "Gerar SQL", false),
                new AcaoTabelaDTO("exportar_dados", "Exportar dados", false),
                new AcaoTabelaDTO("sincronizar_tabela", "Sincronizar tabela", false),
                new AcaoTabelaDTO("copiar_estrutura", "Copiar estrutura", false),
                new AcaoTabelaDTO("comparar_tabela", "Comparar tabela com outro ambiente", true));
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

    private String cacheKey(String token, String idConexao, String ambiente, String base, String schema, String nivel) {
        return String.join("|", "explorador", nivel, String.valueOf(token == null ? 0 : token.hashCode()),
                String.valueOf(idConexao), ambiente.toLowerCase(), base, schema);
    }

    private <T> T unchecked(CheckedSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
