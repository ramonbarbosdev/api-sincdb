package com.api_sincdb.domain.explorador.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.api_sincdb.domain.explorador.dto.AmbienteDTO;
import com.api_sincdb.domain.explorador.dto.BaseResumoDTO;
import com.api_sincdb.domain.explorador.dto.ComparacaoSchemaResumoDTO;
import com.api_sincdb.domain.explorador.dto.DadosTabelaPaginadoDTO;
import com.api_sincdb.domain.explorador.dto.GrafoResponseDTO;
import com.api_sincdb.domain.explorador.dto.SchemaListaResponseDTO;
import com.api_sincdb.domain.explorador.dto.TabelaComparacaoDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaListaResponseDTO;
import com.api_sincdb.domain.explorador.service.ExploradorBaseService;
import com.api_sincdb.domain.explorador.service.ExploradorComparacaoService;
import com.api_sincdb.domain.explorador.service.ExploradorDadosService;
import com.api_sincdb.domain.explorador.service.ExploradorGrafoService;
import com.api_sincdb.domain.explorador.service.ExploradorSchemaLazyService;
import com.api_sincdb.domain.explorador.service.ExploradorTabelaLazyService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/explorador")
public class ExploradorVisualController {

    private final ExploradorBaseService baseService;
    private final ExploradorSchemaLazyService schemaLazyService;
    private final ExploradorTabelaLazyService tabelaLazyService;
    private final ExploradorDadosService dadosService;
    private final ExploradorGrafoService grafoService;
    private final ExploradorComparacaoService comparacaoService;
    private final JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    public ExploradorVisualController(
            ExploradorBaseService baseService,
            ExploradorSchemaLazyService schemaLazyService,
            ExploradorTabelaLazyService tabelaLazyService,
            ExploradorDadosService dadosService,
            ExploradorGrafoService grafoService,
            ExploradorComparacaoService comparacaoService,
            JWTTokenAutenticacaoService jwtTokenAutenticacaoService) {
        this.baseService = baseService;
        this.schemaLazyService = schemaLazyService;
        this.tabelaLazyService = tabelaLazyService;
        this.dadosService = dadosService;
        this.grafoService = grafoService;
        this.comparacaoService = comparacaoService;
        this.jwtTokenAutenticacaoService = jwtTokenAutenticacaoService;
    }

    @GetMapping(value = "/ambientes", produces = "application/json")
    public ResponseEntity<List<AmbienteDTO>> listarAmbientes() {
        return ResponseEntity.ok(baseService.listarAmbientes());
    }

    @GetMapping(value = "/{ambiente}/bases", produces = "application/json")
    public ResponseEntity<List<BaseResumoDTO>> listarBasesAmbiente(
            @PathVariable String ambiente,
            @RequestParam(required = false) String idConexao,
            @RequestParam(defaultValue = "false") boolean incluirQuantidadeSchemas,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

        List<BaseResumoDTO> resumoBases = baseService.listarBases(token, ambiente, idConexao, incluirQuantidadeSchemas);
        return ResponseEntity.ok(resumoBases);
    }

    @GetMapping(value = "/{ambiente}/{base}/schemas", produces = "application/json")
    public ResponseEntity<SchemaListaResponseDTO> listarSchemasAmbiente(
            @PathVariable String ambiente,
            @PathVariable String base,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(schemaLazyService.listarSchemas(token, ambiente, base, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/tabelas", produces = "application/json")
    public ResponseEntity<TabelaListaResponseDTO> listarTabelasAmbiente(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(tabelaLazyService.listarTabelas(token, ambiente, base, esquema, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/tabelas/{tabela}", produces = "application/json")
    public ResponseEntity<TabelaExploracaoDTO> buscarTabelaAmbiente(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(tabelaLazyService.detalharTabela(token, ambiente, base, esquema, tabela, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/tabelas/{tabela}/dados", produces = "application/json")
    public ResponseEntity<DadosTabelaPaginadoDTO> previewDadosTabela(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(dadosService.listarDados(token, ambiente, base, esquema, tabela, page, size,
                idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/grafo", produces = "application/json")
    public ResponseEntity<GrafoResponseDTO> carregarGrafoSchema(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(grafoService.carregarGrafo(token, ambiente, base, esquema, idConexao));
    }

    @GetMapping(value = "/comparacao/{base}/{esquema}", produces = "application/json")
    public ResponseEntity<ComparacaoSchemaResumoDTO> compararSchemaResumo(
            @PathVariable String base,
            @PathVariable String esquema,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(comparacaoService.compararSchemaResumo(token, base, esquema, idConexao));
    }

    @GetMapping(value = "/comparacao/{base}/{esquema}/{tabela}", produces = "application/json")
    public ResponseEntity<TabelaComparacaoDetalheDTO> compararTabelaDetalhe(
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(comparacaoService.compararTabelaDetalhe(token, base, esquema, tabela, idConexao));
    }

}
