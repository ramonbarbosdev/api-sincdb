package com.api_sincdb.domain.forum.dto;

import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.enums.TipoFeedback;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForumPostUpdateRequest {
    private TipoFeedback tipo;
    private String titulo;
    private String descricao;
    private ForumPostStatus statusPost;
}
