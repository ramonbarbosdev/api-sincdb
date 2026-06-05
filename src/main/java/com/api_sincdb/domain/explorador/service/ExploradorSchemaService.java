package com.api_sincdb.domain.explorador.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.api_sincdb.domain.explorador.dto.ResumoComparacaoDTO;
import com.api_sincdb.domain.explorador.dto.SchemaResumoDTO;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService.ComparacaoBanco;

@Service
public class ExploradorSchemaService {

    private final ExploradorComparacaoService comparacaoService;

    public ExploradorSchemaService(ExploradorComparacaoService comparacaoService) {
        this.comparacaoService = comparacaoService;
    }

    public List<SchemaResumoDTO> listarResumoSchemas(String token, String base, String idConexao) throws Exception {
        ComparacaoBanco comparacao = comparacaoService.compararBanco(token, base, null, true, true, idConexao);

        return comparacaoService.schemas(comparacao).stream()
                .map(schema -> montarResumoSchema(comparacao, schema))
                .toList();
    }

    private SchemaResumoDTO montarResumoSchema(ComparacaoBanco comparacao, String schema) {
        Set<String> chaves = comparacaoService.chavesPorSchema(comparacao, schema);
        ResumoComparacaoDTO resumo = comparacaoService.resumir(chaves, comparacao.origem(), comparacao.destino());
        String status = resumo.tabelasDiferentes() == 0 && resumo.ausentesDestino() == 0 && resumo.novasDestino() == 0
                && resumo.colunasDiferentes() == 0 ? "igual" : "diferente";

        return new SchemaResumoDTO(schema, resumo.totalTabelas(), resumo.tabelasIguais(),
                resumo.tabelasDiferentes(), resumo.ausentesDestino(), resumo.novasDestino(),
                resumo.colunasDiferentes(), status);
    }
}
