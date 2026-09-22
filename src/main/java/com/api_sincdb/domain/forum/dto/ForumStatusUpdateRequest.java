package com.api_sincdb.domain.forum.dto;

import com.api_sincdb.enums.ForumEstadoGeral;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForumStatusUpdateRequest {
    private ForumEstadoGeral estadoGeral;
    private String titulo;
    private String mensagem;
    private String proximaVersao;
    private String previsaoRelease;
}
