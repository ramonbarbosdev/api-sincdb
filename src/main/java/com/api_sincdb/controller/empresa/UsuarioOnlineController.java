package com.api_sincdb.controller.empresa;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


import com.api_sincdb.domain.usuario.service.UsuarioOnlineService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(value = "/usuarioonline", produces = "application/json")
@Tag(name = "Usuarios onlines")
public class UsuarioOnlineController {
    @Autowired
    private UsuarioOnlineService usuarioOnlineService;


    @GetMapping("/")
    public ResponseEntity<?> listarUsuariosOnline() {
        return ResponseEntity.ok(usuarioOnlineService.listarUsuariosOnline());
    }
}