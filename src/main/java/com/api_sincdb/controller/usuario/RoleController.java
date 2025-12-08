package com.api_sincdb.controller.usuario;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.controller.base.BaseControllerJpa;
import com.api_sincdb.domain.role.repository.RoleRepository;
import com.api_sincdb.domain.usuario.model.Role;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;
import com.api_sincdb.domain.usuario.service.RoleService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/role")
@Tag(name = "Role")
public class RoleController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RoleService service;

    @PostMapping(value = "/cadastrar", produces = "application/json")
    public ResponseEntity<?> criarNovaRole(@RequestBody Role objeto) {
        roleRepository.save(objeto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("message", "Registro salvo com sucesso"));
    }


    @PutMapping(value = "/atualizar-role/{id_usuario}/{id_role}", produces = "application/json")
    public ResponseEntity<?> atualizarRole(@PathVariable String id_usuario, @PathVariable String id_role) {

        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id_usuario);
        Optional<Role> roleOpt = roleRepository.findById(id_role);

        if (usuarioOpt.isEmpty() || roleOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Usuário ou Role não encontrados"));
        }

        Usuario usuario = usuarioOpt.get();
        Role role = roleOpt.get();

        if (usuario.getRoles().stream().anyMatch(r -> r.getId().equals(id_role))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Esse usuário já possui essa Role!"));
        }

        usuario.getRoles().add(role);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("message", "Acesso incluído para o usuário " + usuario.getNome()));
    }


    @GetMapping(value = "/obter-por-usuario/{id_usuario}", produces = "application/json")
    public ResponseEntity<?> obterRolesUsuario(@PathVariable String id_usuario) {

        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id_usuario);

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Usuário não encontrado"));
        }

        return ResponseEntity.ok(usuarioOpt.get().getRoles());
    }


    @GetMapping(produces = "application/json")
    public ResponseEntity<?> obterTodasRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }


    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> obterRoleId(@PathVariable String id) {

        Optional<Role> optionalRole = roleRepository.findById(id);

        return optionalRole.isPresent()
                ? ResponseEntity.ok(optionalRole.get())
                : ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Role não encontrada"));
    }


    @DeleteMapping(value = "/remover-por-usuario/{id_usuario}", produces = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removerRolesUsuario(@PathVariable String id_usuario) {

        Optional<Usuario> usuarioOpt = usuarioRepository.findById(id_usuario);

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Usuário não encontrado"));
        }

        Usuario usuario = usuarioOpt.get();
        usuario.getRoles().clear();
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(Map.of("message", "Todos os acessos foram removidos do usuário!"));
    }


    @DeleteMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<?> deletar(@PathVariable String id) {

        if (!roleRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Role não encontrada!"));
        }

        roleRepository.deleteById(id);

        return ResponseEntity.ok(Map.of("message", "Removido com sucesso!"));
    }
}