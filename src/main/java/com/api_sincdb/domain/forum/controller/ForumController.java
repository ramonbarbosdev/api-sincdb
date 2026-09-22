package com.api_sincdb.domain.forum.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.forum.dto.ForumCurtidaResponse;
import com.api_sincdb.domain.forum.dto.ForumMetricasResponse;
import com.api_sincdb.domain.forum.dto.ForumPostCreateRequest;
import com.api_sincdb.domain.forum.dto.ForumPostResponse;
import com.api_sincdb.domain.forum.dto.ForumPostUpdateRequest;
import com.api_sincdb.domain.forum.dto.ForumStatusUpdateRequest;
import com.api_sincdb.domain.forum.model.ForumPost;
import com.api_sincdb.domain.forum.model.ForumSystemStatus;
import com.api_sincdb.domain.forum.service.ForumCurtidaService;
import com.api_sincdb.domain.forum.service.ForumMetricasService;
import com.api_sincdb.domain.forum.service.ForumPostService;
import com.api_sincdb.domain.forum.service.ForumStatusService;
import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/forum")
public class ForumController {

    @Autowired
    private ForumPostService postService;

    @Autowired
    private ForumCurtidaService curtidaService;

    @Autowired
    private ForumStatusService statusService;

    @Autowired
    private ForumMetricasService metricasService;

    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    @GetMapping(value = "/metricas", produces = "application/json")
    public ResponseEntity<ForumMetricasResponse> metricas(HttpServletRequest request) {
        return ResponseEntity.ok(metricasService.obter(role(request)));
    }

    @GetMapping(value = "/status", produces = "application/json")
    public ResponseEntity<ForumSystemStatus> status() {
        return ResponseEntity.ok(statusService.obter());
    }

    @PutMapping(value = "/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> atualizarStatus(@RequestBody ForumStatusUpdateRequest body, HttpServletRequest request) {
        if (!postServiceIsDev(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Acesso negado."));
        }
        return ResponseEntity.ok(statusService.atualizar(body));
    }

    @GetMapping(value = "/posts", produces = "application/json")
    public ResponseEntity<List<ForumPostResponse>> listarPosts(
            @RequestParam(defaultValue = "todos") String filtro,
            @RequestParam(defaultValue = "destaque") String ordenacao,
            HttpServletRequest request) {
        return ResponseEntity.ok(postService.listar(filtro, ordenacao, role(request)));
    }

    @PostMapping(value = "/posts", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> criarPost(@RequestBody ForumPostCreateRequest body, HttpServletRequest request) {
        try {
            ForumPost saved = postService.criar(body);
            return new ResponseEntity<>(postService.mapResponse(saved, role(request)), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping(value = "/posts/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> atualizarPost(
            @PathVariable String id,
            @RequestBody ForumPostUpdateRequest body,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(postService.atualizar(id, body, role(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping(value = "/posts/{id}")
    public ResponseEntity<?> excluirPost(@PathVariable String id, HttpServletRequest request) {
        try {
            postService.excluir(id, role(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/posts/{id}/curtir", produces = "application/json")
    public ResponseEntity<ForumCurtidaResponse> curtir(@PathVariable String id) {
        return ResponseEntity.ok(curtidaService.toggle(id));
    }

    @PatchMapping(value = "/posts/{id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> patchStatusPost(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {
        if (!postServiceIsDev(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Acesso negado."));
        }
        try {
            ForumPostStatus status = ForumPostStatus.valueOf(body.get("statusPost").toUpperCase());
            return ResponseEntity.ok(postService.patchStatus(id, status, role(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status inválido."));
        }
    }

    private String role(HttpServletRequest request) {
        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        return jwtTokenAutenticacaoService.extractRole(token);
    }

    private boolean postServiceIsDev(HttpServletRequest request) {
        return "ROLE_DEV".equals(role(request));
    }
}
