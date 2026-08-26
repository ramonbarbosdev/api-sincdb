package com.api_sincdb.domain.operacao.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.operacao.dto.SyncQueueEnqueueRequest;
import com.api_sincdb.domain.operacao.dto.SyncQueueStatusResponse;
import com.api_sincdb.domain.operacao.model.SyncQueueItem;
import com.api_sincdb.domain.operacao.service.SyncQueueService;
import com.api_sincdb.helper.JwtHelper;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/sync-queue")
public class SyncQueueController {

  @Autowired
  private SyncQueueService syncQueueService;

  @Autowired
  private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

  @Autowired
  private JwtHelper jwtHelper;

  @GetMapping(produces = "application/json")
  public ResponseEntity<List<SyncQueueItem>> list(HttpServletRequest request) {
    String usuario = usuario(request);
    return ResponseEntity.ok(syncQueueService.listForUser(usuario));
  }

  @GetMapping(value = "/status", produces = "application/json")
  public ResponseEntity<SyncQueueStatusResponse> status(HttpServletRequest request) {
    String usuario = usuario(request);
    return ResponseEntity.ok(syncQueueService.statusForUser(usuario));
  }

  @PostMapping(consumes = "application/json", produces = "application/json")
  public ResponseEntity<SyncQueueItem> enqueue(
      @RequestBody SyncQueueEnqueueRequest body,
      HttpServletRequest request) {
    String usuario = usuario(request);
    try {
      SyncQueueItem item = syncQueueService.enqueue(usuario, body);
      return new ResponseEntity<>(item, HttpStatus.CREATED);
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping(value = "/run", produces = "application/json")
  public ResponseEntity<Map<String, Boolean>> run(HttpServletRequest request) {
    String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
    String usuario = jwtHelper.extrairUsuario(token);
    syncQueueService.start(usuario, token);
    return ResponseEntity.ok(Map.of("started", true));
  }

  @DeleteMapping(value = "/{id}")
  public ResponseEntity<Void> remove(@PathVariable String id, HttpServletRequest request) {
    String usuario = usuario(request);
    try {
      syncQueueService.remove(usuario, id);
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    } catch (IllegalStateException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }
  }

  @DeleteMapping
  public ResponseEntity<Void> clear(HttpServletRequest request) {
    String usuario = usuario(request);
    syncQueueService.clear(usuario);
    return ResponseEntity.noContent().build();
  }

  private String usuario(HttpServletRequest request) {
    String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
    return jwtHelper.extrairUsuario(token);
  }
}
