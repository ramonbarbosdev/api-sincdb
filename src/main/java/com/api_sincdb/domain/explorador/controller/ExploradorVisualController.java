package com.api_sincdb.domain.explorador.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.explorador.dto.ExploradorVisualResponseDTO;
import com.api_sincdb.domain.explorador.service.ExploradorVisualService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/explorador")
public class ExploradorVisualController {

    private final ExploradorVisualService service;
    private final JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    public ExploradorVisualController(
            ExploradorVisualService service,
            JWTTokenAutenticacaoService jwtTokenAutenticacaoService) {
        this.service = service;
        this.jwtTokenAutenticacaoService = jwtTokenAutenticacaoService;
    }

    @GetMapping(value = "/{base}/{esquema}/comparar", produces = "application/json")
    public ResponseEntity<ExploradorVisualResponseDTO> comparar(
            @PathVariable String base,
            @PathVariable String esquema,
            HttpServletRequest request) throws Exception {

        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);

        return ResponseEntity.ok(service.comparar(token, base, esquema));
    }
}
