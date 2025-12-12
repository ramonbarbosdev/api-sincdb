package com.api_sincdb.domain.info.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.info.model.SincronizacaoSchema;
import com.api_sincdb.domain.info.repository.SincronizacaoSchemaRepository;
import com.api_sincdb.domain.info.service.SincronizacaoSchemaService;
import com.api_sincdb.helper.JwtHelper;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@RestController
@RequestMapping("/info")
public class InfoController {

    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    @Autowired
    private SincronizacaoSchemaService sincronizacaoSchemaService;

    @Autowired
    private JwtHelper jwtHelper;

    @GetMapping(value = "/atividade", produces = "application/json")
    public ResponseEntity<?> obterUltimasAtividade(HttpServletRequest request) throws InterruptedException {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        String usuario = jwtHelper.extrairUsuario(token);

        List<SincronizacaoSchema> list = sincronizacaoSchemaService.listarPorUsuario(usuario);

        return new ResponseEntity<>(list, HttpStatus.NOT_FOUND);

    }
}