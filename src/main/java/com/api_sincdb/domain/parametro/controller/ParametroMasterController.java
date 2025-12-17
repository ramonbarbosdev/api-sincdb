package com.api_sincdb.domain.parametro.controller;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.CrudRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.controller.base.BaseController;
import com.api_sincdb.controller.base.BaseControllerMongo;
import com.api_sincdb.domain.parametro.model.ParametroMaster;
import com.api_sincdb.domain.parametro.repository.ParametroMasterRepository;
import com.api_sincdb.domain.parametro.service.ParametroMasterService;
import com.api_sincdb.domain.sistema.service.ValidacaoService;
import com.api_sincdb.domain.usuario.dto.PerfilDTO;
import com.api_sincdb.enums.TipoParametro;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/parametromaster", produces = "application/json")
@Tag(name = "Parametro Master ")
public class ParametroMasterController extends BaseControllerMongo<ParametroMaster, String> {

    @Autowired
    private ValidacaoService validacaoService;

    @Autowired
    private ParametroMasterRepository objetoRepository;

    @Autowired
    private ParametroMasterService service;

    @PostMapping(value = "/cadastrar", produces = "application/json")
    public ResponseEntity<?> cadastrar(@RequestBody ParametroMaster objeto) throws Exception {

        service.salvar(objeto);

        return new ResponseEntity<>(Map.of("message", "Registro salvo com sucesso"), HttpStatus.CREATED);
    }

    @GetMapping(value = "/sequencia", produces = "application/json")
    @Operation(summary = "Gerar sequencia")
    public ResponseEntity<?> obterSequencia() throws Exception {

        String resposta = service.sequencia();

        return new ResponseEntity<>(Map.of("sequencia", resposta), HttpStatus.OK);
    }

    @GetMapping("/tipo-parametro")
    public ResponseEntity<TipoParametro[]> obterTipoParaetro() {
        return ResponseEntity.ok(TipoParametro.values());
    }

}