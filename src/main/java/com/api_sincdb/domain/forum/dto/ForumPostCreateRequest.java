package com.api_sincdb.domain.forum.dto;

import com.api_sincdb.enums.TipoFeedback;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForumPostCreateRequest {
    private TipoFeedback tipo;
    private String titulo;
    private String descricao;
}
