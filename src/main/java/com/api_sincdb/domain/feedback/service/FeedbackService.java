package com.api_sincdb.domain.feedback.service;



import java.time.LocalDateTime;
import java.util.LinkedHashSet;

import java.util.List;

import java.util.Optional;

import java.util.Set;



import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Service;



import com.api_sincdb.context.TenantRuntimeContext;

import com.api_sincdb.domain.feedback.dto.FeedbackCreateRequest;

import com.api_sincdb.domain.feedback.model.FeedbackReport;

import com.api_sincdb.domain.feedback.repository.FeedbackReportRepository;

import com.api_sincdb.domain.usuario.model.Usuario;

import com.api_sincdb.domain.usuario.repository.UsuarioRepository;

import com.api_sincdb.enums.TipoRole;



@Service

public class FeedbackService {



    @Autowired

    private FeedbackReportRepository repository;



    @Autowired

    private UsuarioRepository usuarioRepository;



    public FeedbackReport criar(FeedbackCreateRequest request) {

        validar(request);



        Usuario autor = buscarUsuarioAtual().orElse(null);

        String chaveAutor = chaveAutor(autor);



        FeedbackReport report = new FeedbackReport();

        report.setTipo(request.getTipo());

        report.setTitulo(request.getTitulo().trim());

        report.setDescricao(request.getDescricao().trim());

        report.setUsuario(chaveAutor);

        report.setNomeUsuario(nomeExibicao(autor, chaveAutor));

        report.setIdEmpresa(TenantRuntimeContext.getIdEmpresa());

        report.setCreatedAt(LocalDateTime.now());



        return repository.save(report);

    }



    public List<FeedbackReport> listarDoUsuario() {

        Set<String> chaves = chavesUsuarioAtual();

        if (chaves.isEmpty()) {

            return List.of();

        }



        return repository.findByUsuarioInOrderByCreatedAtDesc(chaves).stream()

                .peek(this::preencherNomeSeAusente)

                .toList();

    }



    public List<FeedbackReport> listarTodos(String role) {

        if (!TipoRole.ROLE_DEV.name().equals(role)) {

            throw new IllegalStateException("Acesso negado.");

        }

        return repository.findAllByOrderByCreatedAtDesc().stream()

                .peek(this::preencherNomeSeAusente)

                .toList();

    }



    private Set<String> chavesUsuarioAtual() {

        Set<String> chaves = new LinkedHashSet<>();

        String idUsuario = TenantRuntimeContext.getIdUsuario();

        String login = TenantRuntimeContext.getLogin();

        if (idUsuario != null && !idUsuario.isBlank()) {

            chaves.add(idUsuario);

        }

        if (login != null && !login.isBlank()) {

            chaves.add(login);

        }

        return chaves;

    }



    private String chaveAutor(Usuario autor) {

        if (autor != null && autor.getId() != null && !autor.getId().isBlank()) {

            return autor.getId();

        }

        String idUsuario = TenantRuntimeContext.getIdUsuario();

        if (idUsuario != null && !idUsuario.isBlank()) {

            return idUsuario;

        }

        return TenantRuntimeContext.getLogin();

    }



    private Optional<Usuario> buscarUsuarioAtual() {

        String idUsuario = TenantRuntimeContext.getIdUsuario();

        if (idUsuario != null && !idUsuario.isBlank()) {

            Optional<Usuario> porId = usuarioRepository.findById(idUsuario);

            if (porId.isPresent()) {

                return porId;

            }

        }



        String login = TenantRuntimeContext.getLogin();

        if (login != null && !login.isBlank()) {

            Usuario porLogin = usuarioRepository.findByLogin(login);

            if (porLogin != null) {

                return Optional.of(porLogin);

            }

        }



        return Optional.empty();

    }



    private Optional<Usuario> buscarPorChaveArmazenada(String chave) {

        if (chave == null || chave.isBlank()) {

            return Optional.empty();

        }



        Optional<Usuario> porId = usuarioRepository.findById(chave);

        if (porId.isPresent()) {

            return porId;

        }



        Usuario porLogin = usuarioRepository.findByLogin(chave);

        return Optional.ofNullable(porLogin);

    }



    private String nomeExibicao(Usuario usuario, String fallback) {

        if (usuario != null && usuario.getNome() != null && !usuario.getNome().isBlank()) {

            return usuario.getNome().trim();

        }

        if (fallback != null && !fallback.isBlank() && !pareceIdMongo(fallback)) {

            return fallback;

        }

        return "Usuário";

    }



    private void preencherNomeSeAusente(FeedbackReport report) {

        if (report == null) {

            return;

        }



        String nomeAtual = report.getNomeUsuario();

        if (nomeAtual != null

                && !nomeAtual.isBlank()

                && !nomeAtual.equals(report.getUsuario())

                && !pareceIdMongo(nomeAtual)) {

            return;

        }



        Usuario usuario = buscarPorChaveArmazenada(report.getUsuario()).orElse(null);

        report.setNomeUsuario(nomeExibicao(usuario, report.getUsuario()));

    }



    private boolean pareceIdMongo(String value) {

        return value != null && value.matches("[a-f0-9A-F]{24}");

    }



    private void validar(FeedbackCreateRequest request) {

        if (request == null || request.getTipo() == null) {

            throw new IllegalArgumentException("Informe o tipo (bug ou sugestão).");

        }

        if (request.getTitulo() == null || request.getTitulo().trim().length() < 3) {

            throw new IllegalArgumentException("O título deve ter pelo menos 3 caracteres.");

        }

        if (request.getDescricao() == null || request.getDescricao().trim().length() < 10) {

            throw new IllegalArgumentException("Descreva com pelo menos 10 caracteres.");

        }

    }

}


