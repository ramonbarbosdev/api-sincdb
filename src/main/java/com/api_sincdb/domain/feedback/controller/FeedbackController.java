package com.api_sincdb.domain.feedback.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.api_sincdb.domain.feedback.dto.FeedbackCreateRequest;
import com.api_sincdb.domain.feedback.model.FeedbackReport;
import com.api_sincdb.domain.feedback.service.FeedbackService;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private JWTTokenAutenticacaoService jwtTokenAutenticacaoService;

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> criar(@RequestBody FeedbackCreateRequest body, HttpServletRequest request) {
        try {
            FeedbackReport saved = feedbackService.criar(body);
            return new ResponseEntity<>(saved, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping(produces = "application/json")
    public ResponseEntity<List<FeedbackReport>> listar(HttpServletRequest request) {
        return ResponseEntity.ok(feedbackService.listarDoUsuario());
    }

    @GetMapping(value = "/admin", produces = "application/json")
    public ResponseEntity<?> listarAdmin(HttpServletRequest request) {
        String token = jwtTokenAutenticacaoService.obterTokenHeaderOuCookie(request);
        String role = jwtTokenAutenticacaoService.extractRole(token);
        try {
            return ResponseEntity.ok(feedbackService.listarTodos(role));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }
}
