package com.api_sincdb.domain.explorador.service;

import java.util.Comparator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.ForeignKeyDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO.IndiceDetalheDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.TabelaInfo;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService.ComparacaoBanco;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService.TableDiff;

@Service
public class ExploradorTabelaService {

    private final ExploradorComparacaoService comparacaoService;
    private final ExploradorSqlPreviewService sqlPreviewService;

    public ExploradorTabelaService(ExploradorComparacaoService comparacaoService,
            ExploradorSqlPreviewService sqlPreviewService) {
        this.comparacaoService = comparacaoService;
        this.sqlPreviewService = sqlPreviewService;
    }

    public TabelaDetalheDTO buscarDetalhe(String token, String base, String schema, String tabela, String idConexao)
            throws Exception {
        ComparacaoBanco comparacao = comparacaoService.compararBanco(token, base, schema, true, true, idConexao);
        String tabelaId = schema + "." + tabela;
        TabelaInfo origem = comparacao.origem().tabelas().get(tabelaId);
        TabelaInfo destino = comparacao.destino().tabelas().get(tabelaId);

        if (origem == null && destino == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela nao encontrada");
        }

        TabelaInfo baseInfo = origem != null ? origem : destino;
        TableDiff diff = comparacaoService.compararTabela(origem, destino);

        return new TabelaDetalheDTO(
                tabelaId,
                baseInfo.schema(),
                baseInfo.nome(),
                diff.status(),
                diff.colunas(),
                baseInfo.indices().stream()
                        .map(indice -> new IndiceDetalheDTO(indice.nome(), indice.colunas(), indice.unico(),
                                diff.status()))
                        .sorted(Comparator.comparing(IndiceDetalheDTO::nome))
                        .toList(),
                baseInfo.foreignKeys().stream()
                        .map(fk -> new ForeignKeyDetalheDTO(fk.nome(), fk.coluna(), fk.tabelaReferencia(),
                                fk.colunaReferencia(), diff.status()))
                        .sorted(Comparator.comparing(ForeignKeyDetalheDTO::nome))
                        .toList(),
                diff.observacoes(),
                sqlPreviewService.juntarPreview(diff.sqlPreview()));
    }
}
