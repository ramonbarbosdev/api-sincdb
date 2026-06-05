package com.api_sincdb.domain.explorador.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.api_sincdb.domain.explorador.dto.DiagramEdgeDTO;
import com.api_sincdb.domain.explorador.dto.DiagramNodeDTO;
import com.api_sincdb.domain.explorador.dto.DiagramResponseDTO;
import com.api_sincdb.domain.explorador.dto.ResumoComparacaoDTO;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.ForeignKeyInfo;
import com.api_sincdb.domain.explorador.metadata.PostgresMetadataReader.TabelaInfo;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService.ComparacaoBanco;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService.TableDiff;

@Service
public class ExploradorDiagramService {

    private final ExploradorComparacaoService comparacaoService;

    public ExploradorDiagramService(ExploradorComparacaoService comparacaoService) {
        this.comparacaoService = comparacaoService;
    }

    public DiagramResponseDTO compararSchema(String token, String base, String schema, String idConexao)
            throws Exception {
        ComparacaoBanco comparacao = comparacaoService.compararBanco(token, base, schema, true, true, idConexao);
        Set<String> chaves = comparacaoService.chavesPorSchema(comparacao, schema);

        List<DiagramNodeDTO> nodes = chaves.stream()
                .map(chave -> montarNode(comparacao, chave))
                .sorted(Comparator.comparing(DiagramNodeDTO::id))
                .toList();

        List<DiagramEdgeDTO> edges = montarEdges(comparacao, chaves);
        ResumoComparacaoDTO resumo = comparacaoService.resumir(chaves, comparacao.origem(), comparacao.destino());

        return new DiagramResponseDTO(base, schema, nodes, edges, resumo);
    }

    public DiagramResponseDTO grafoTabela(String token, String base, String schema, String tabela, String idConexao)
            throws Exception {
        ComparacaoBanco comparacao = comparacaoService.compararBanco(token, base, schema, true, true, idConexao);
        String tabelaId = schema + "." + tabela;
        Set<String> chaves = new LinkedHashSet<>();
        chaves.add(tabelaId);

        adicionarRelacionadas(chaves, tabelaId, comparacao.origem().tabelas().get(tabelaId));
        adicionarRelacionadas(chaves, tabelaId, comparacao.destino().tabelas().get(tabelaId));
        comparacao.origem().tabelas().values().forEach(info -> adicionarSeReferencia(chaves, tabelaId, info));
        comparacao.destino().tabelas().values().forEach(info -> adicionarSeReferencia(chaves, tabelaId, info));

        List<DiagramNodeDTO> nodes = chaves.stream()
                .filter(chave -> comparacao.origem().tabelas().containsKey(chave)
                        || comparacao.destino().tabelas().containsKey(chave))
                .map(chave -> montarNode(comparacao, chave))
                .sorted(Comparator.comparing(DiagramNodeDTO::id))
                .toList();

        List<DiagramEdgeDTO> edges = montarEdges(comparacao, chaves);
        ResumoComparacaoDTO resumo = comparacaoService.resumir(chaves, comparacao.origem(), comparacao.destino());
        return new DiagramResponseDTO(base, schema, nodes, edges, resumo);
    }

    private DiagramNodeDTO montarNode(ComparacaoBanco comparacao, String chave) {
        TabelaInfo origem = comparacao.origem().tabelas().get(chave);
        TabelaInfo destino = comparacao.destino().tabelas().get(chave);
        TabelaInfo baseInfo = origem != null ? origem : destino;
        TableDiff diff = comparacaoService.compararTabela(origem, destino);

        int totalFks = origem != null ? origem.foreignKeys().size() : destino.foreignKeys().size();
        return new DiagramNodeDTO(chave, baseInfo.schema(), baseInfo.nome(), diff.status(), diff.totalColunas(),
                diff.totalDiferencas(), totalFks);
    }

    private List<DiagramEdgeDTO> montarEdges(ComparacaoBanco comparacao, Set<String> nodesPermitidos) {
        Map<String, ForeignKeyInfo> origem = mapearFks(comparacao.origem().tabelas(), nodesPermitidos);
        Map<String, ForeignKeyInfo> destino = mapearFks(comparacao.destino().tabelas(), nodesPermitidos);

        Set<String> chaves = new LinkedHashSet<>();
        chaves.addAll(origem.keySet());
        chaves.addAll(destino.keySet());

        List<DiagramEdgeDTO> edges = new ArrayList<>();
        for (String chave : chaves) {
            ForeignKeyInfo fkOrigem = origem.get(chave);
            ForeignKeyInfo fkDestino = destino.get(chave);
            ForeignKeyInfo fk = fkOrigem != null ? fkOrigem : fkDestino;
            String source = chave.substring(0, chave.indexOf('|'));

            String status;
            if (fkOrigem != null && fkDestino == null) {
                status = "ausente_destino";
            } else if (fkOrigem == null) {
                status = "novo_destino";
            } else {
                status = fkOrigem.assinatura().equals(fkDestino.assinatura()) ? "igual" : "diferente";
            }

            edges.add(new DiagramEdgeDTO(fk.nome(), source, fk.tabelaReferencia(), status, "N:1"));
        }

        return edges.stream().sorted(Comparator.comparing(DiagramEdgeDTO::id)).toList();
    }

    private Map<String, ForeignKeyInfo> mapearFks(Map<String, TabelaInfo> tabelas, Set<String> nodesPermitidos) {
        Map<String, ForeignKeyInfo> fks = new LinkedHashMap<>();
        tabelas.forEach((source, tabela) -> tabela.foreignKeys().stream()
                .filter(fk -> nodesPermitidos.contains(source) && nodesPermitidos.contains(fk.tabelaReferencia()))
                .forEach(fk -> fks.put(fk.sourceTargetKey(source), fk)));
        return fks;
    }

    private void adicionarRelacionadas(Set<String> chaves, String tabelaId, TabelaInfo tabela) {
        if (tabela == null) {
            return;
        }
        tabela.foreignKeys().forEach(fk -> chaves.add(fk.tabelaReferencia()));
    }

    private void adicionarSeReferencia(Set<String> chaves, String tabelaId, TabelaInfo tabela) {
        if (tabela == null) {
            return;
        }
        if (tabela.foreignKeys().stream().anyMatch(fk -> tabelaId.equals(fk.tabelaReferencia()))) {
            chaves.add(tabela.id());
        }
    }
}
