package com.api_sincdb.domain.forum.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.forum.dto.ForumCurtidaResponse;
import com.api_sincdb.domain.forum.model.ForumCurtida;
import com.api_sincdb.domain.forum.repository.ForumCurtidaRepository;

@Service
public class ForumCurtidaService {

    @Autowired
    private ForumCurtidaRepository curtidaRepository;

    @Autowired
    private ForumPostService postService;

    @Autowired
    private ForumContextHelper context;

    public ForumCurtidaResponse toggle(String postId) {
        postService.buscarObrigatorio(postId);
        String idUsuario = context.idUsuarioAtual();
        if (idUsuario == null || idUsuario.isBlank()) {
            throw new IllegalStateException("Usuário não identificado.");
        }

        var existente = curtidaRepository.findByPostIdAndIdUsuario(postId, idUsuario);
        if (existente.isPresent()) {
            curtidaRepository.delete(existente.get());
        } else {
            ForumCurtida curtida = new ForumCurtida();
            curtida.setPostId(postId);
            curtida.setIdUsuario(idUsuario);
            curtida.setCreatedAt(LocalDateTime.now());
            curtidaRepository.save(curtida);
        }

        postService.sincronizarContadorCurtidas(postId);
        var post = postService.buscarObrigatorio(postId);

        ForumCurtidaResponse response = new ForumCurtidaResponse();
        response.setCurtidasCount(post.getCurtidasCount());
        response.setCurtidoPorMim(!existente.isPresent());
        return response;
    }
}
