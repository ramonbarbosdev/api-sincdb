package com.api_sincdb.controller.usuario;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.usuario.dto.AuthRegisterDTO;
import com.api_sincdb.domain.usuario.dto.LoginRequestDTO;
import com.api_sincdb.domain.usuario.dto.LoginResponseDTO;
import com.api_sincdb.domain.usuario.dto.MeResponseDTO;
import com.api_sincdb.domain.usuario.dto.SelecionarOrganizacaoRequestDTO;
import com.api_sincdb.domain.usuario.dto.SelecionarOrganizacaoResponseDTO;
import com.api_sincdb.domain.usuario.service.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(value = "/auth")
@Tag(name = "Autenticacao")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @Operation(summary = "Autenticacao de usuario", description = "Faz login com CPF e senha")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticacao aceita"),
            @ApiResponse(responseCode = "401", description = "Nao autorizado")
    })
    @PostMapping(value = "/login", produces = "application/json")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) throws Exception {
        return ResponseEntity.ok(service.login(request));
    }

    @PostMapping(value = "/selecionar-organizacao", produces = "application/json")
    public ResponseEntity<SelecionarOrganizacaoResponseDTO> selecionarOrganizacao(
            @Valid @RequestBody SelecionarOrganizacaoRequestDTO request) throws Exception {
        return ResponseEntity.ok(service.selecionarOrganizacao(request.idOrganizacao()));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponseDTO> me() throws Exception {
        return ResponseEntity.ok(service.me());
    }

    @Operation(summary = "Criacao de usuario", description = "Faz registro do usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario criado"),
            @ApiResponse(responseCode = "409", description = "Usuario ja existe")
    })
    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity<?> register(@RequestBody AuthRegisterDTO obj) {
        try {
            Map cadastroResponse = service.efetuarCadastro(obj, null);
            return ResponseEntity.status(HttpStatus.CREATED).body(cadastroResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/logout", produces = "application/json")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) throws Exception {
        service.logout(request, response);
        return ResponseEntity.noContent().build();
    }
}
