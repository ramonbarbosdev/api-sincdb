package com.api_sincdb.domain.feedback.dto;

import com.api_sincdb.enums.TipoFeedback;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeedbackCreateRequest {

    private TipoFeedback tipo;
    private String titulo;
    private String descricao;
}
