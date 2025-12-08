package com.api_sincdb.controller.usuario;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.usuario.dto.AuthLoginDTO;
import com.api_sincdb.domain.usuario.dto.AuthRegisterDTO;
import com.api_sincdb.domain.usuario.model.Role;
import com.api_sincdb.domain.usuario.model.Usuario;

import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.domain.usuario.service.AuthService;
import com.api_sincdb.domain.usuario.service.UsuarioService;
import com.api_sincdb.enums.TipoRole;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(value = "/auth")
@Tag(name = "Authenticação")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private AuthService service;

    @Operation(summary = "Autenticação de usuario", description = "Faz login com login e senha")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Autenticação aceita"),
            @ApiResponse(responseCode = "401", description = "Não autorizado")
    })
    @PostMapping(value = "/login", produces = "application/json")
    public ResponseEntity login(@RequestBody AuthLoginDTO obj, HttpServletRequest request, HttpServletResponse response)
            throws Exception {

        try {

            Map loginResponse = service.efetuarLogin(obj, response,request);

            return ResponseEntity.ok().body(loginResponse);
        } catch (Exception e) {
            service.logout(request, response);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @Operation(summary = "Criaçao de usuario", description = "Faz registro do usuario")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario criado"),
            @ApiResponse(responseCode = "409", description = "Usuario ja existe")
    })
    @PostMapping(value = "/register", produces = "application/json")
    public ResponseEntity register(@RequestBody AuthRegisterDTO obj) {

        String login = obj.getLogin();
        String nome = obj.getNome();
        String senha = obj.getSenha();

        try {
            Map cadastroResponse = service.efetuarCadastro(login, senha, nome, null);
            return ResponseEntity.status(HttpStatus.CREATED).body(cadastroResponse);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }

    }

    @PostMapping(value = "/logout", produces = "application/json")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) throws Exception {

        Boolean flLogout = service.logout(request, response);
        if (flLogout) {
            return ResponseEntity.ok(Map.of("message", "Logout realizado com sucesso."));
        }
        return ResponseEntity.status(401).body(Map.of("message", "Usuário não autenticado."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Não autenticado");
        }

        var login = authentication.getPrincipal(); // normalmente UserDetails
        Usuario user = usuarioRepository.findByLogin(String.valueOf(login));
        return ResponseEntity
                .ok()
                .body(Map.of(
                        "login", user.getLogin(),
                        "role", user.getRoles().iterator().next().getNomeRole()));
    }

}
