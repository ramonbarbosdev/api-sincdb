package com.api_sincdb.domain.explorador.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import com.api_sincdb.domain.explorador.dto.DadosTabelaDTO;
import com.api_sincdb.domain.explorador.dto.DiagramResponseDTO;
import com.api_sincdb.domain.explorador.dto.SchemaResumoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaExploracaoDTO;
import com.api_sincdb.domain.explorador.dto.TabelaDetalheDTO;
import com.api_sincdb.domain.explorador.dto.TabelaResumoDTO;
import com.api_sincdb.domain.explorador.service.ExploradorAmbienteService;
import com.api_sincdb.domain.explorador.service.ExploradorDiagramService;
import com.api_sincdb.domain.explorador.service.ExploradorSchemaService;
import com.api_sincdb.domain.explorador.service.ExploradorTabelaService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/explorador")
public class ExploradorVisualController {

    private final ExploradorAmbienteService ambienteService;
    private final ExploradorSchemaService schemaService;
    private final ExploradorDiagramService diagramService;
    private final ExploradorTabelaService tabelaService;
    private final JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    public ExploradorVisualController(
            ExploradorAmbienteService ambienteService,
            ExploradorSchemaService schemaService,
            ExploradorDiagramService diagramService,
            ExploradorTabelaService tabelaService,
            JWTTokenAutenticacaoService jwtTokenAutenticacaoService) {
        this.ambienteService = ambienteService;
        this.schemaService = schemaService;
        this.diagramService = diagramService;
        this.tabelaService = tabelaService;
        this.jwtTokenAutenticacaoService = jwtTokenAutenticacaoService;
    }

    @GetMapping(value = "/{ambiente}/bases", produces = "application/json")
    public ResponseEntity<List<String>> listarBasesAmbiente(
            @PathVariable String ambiente,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(ambienteService.listarBases(token, ambiente, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/schemas", produces = "application/json")
    public ResponseEntity<List<String>> listarSchemasAmbiente(
            @PathVariable String ambiente,
            @PathVariable String base,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(ambienteService.listarSchemas(token, ambiente, base, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/tabelas", produces = "application/json")
    public ResponseEntity<List<TabelaResumoDTO>> listarTabelasAmbiente(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(ambienteService.listarTabelas(token, ambiente, base, esquema, idConexao));
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
        return ResponseEntity.ok(ambienteService.buscarTabela(token, ambiente, base, esquema, tabela, idConexao));
    }

    @GetMapping(value = "/{ambiente}/{base}/{esquema}/tabelas/{tabela}/dados", produces = "application/json")
    public ResponseEntity<DadosTabelaDTO> previewDadosTabela(
            @PathVariable String ambiente,
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(ambienteService.previewDados(token, ambiente, base, esquema, tabela, limit,
                idConexao));
    }

    @GetMapping(value = "/{base}/schemas/comparar", produces = "application/json")
    public ResponseEntity<List<SchemaResumoDTO>> listarSchemasComparados(
            @PathVariable String base,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(schemaService.listarResumoSchemas(token, base, idConexao));
    }

    @GetMapping(value = "/{base}/{esquema}/comparar", produces = "application/json")
    public ResponseEntity<DiagramResponseDTO> compararSchema(
            @PathVariable String base,
            @PathVariable String esquema,
            @RequestParam(defaultValue = "true") boolean incluirIndices,
            @RequestParam(defaultValue = "true") boolean incluirFks,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(diagramService.compararSchema(token, base, esquema, idConexao));
    }

    @GetMapping(value = "/{base}/{esquema}/tabelas/{tabela}/detalhe", produces = "application/json")
    public ResponseEntity<TabelaDetalheDTO> buscarDetalheTabela(
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(tabelaService.buscarDetalhe(token, base, esquema, tabela, idConexao));
    }

    @GetMapping(value = "/{base}/{esquema}/tabelas/{tabela}/grafo", produces = "application/json")
    public ResponseEntity<DiagramResponseDTO> grafoTabela(
            @PathVariable String base,
            @PathVariable String esquema,
            @PathVariable String tabela,
            @RequestParam(required = false) String idConexao,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return ResponseEntity.ok(diagramService.grafoTabela(token, base, esquema, tabela, idConexao));
    }
}
