package com.api_sincdb.domain.forum.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.api_sincdb.domain.forum.dto.ForumMetricasResponse;
import com.api_sincdb.enums.TipoFeedback;

@Service
public class ForumMetricasService {

    @Autowired
    private ForumStatusService statusService;

    @Autowired
    private ForumPostService postService;

    public ForumMetricasResponse obter(String role) {
        ForumMetricasResponse response = new ForumMetricasResponse();
        response.setStatus(statusService.obter());
        response.setBugsAbertos(postService.contarAbertos(TipoFeedback.BUG));
        response.setSugestoesAbertas(postService.contarAbertos(TipoFeedback.SUGESTAO));
        response.setPostsDestaque(postService.topDestaques(3, role));
        return response;
    }
}
