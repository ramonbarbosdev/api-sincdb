package com.api_sincdb.domain.forum.dto;

import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.enums.TipoFeedback;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForumPostResponse {
    private String id;
    private TipoFeedback tipo;
    private String titulo;
    private String descricao;
    private String idUsuario;
    private String nomeUsuario;
    /** URL da foto de perfil do autor (quando cadastrada). */
    private String imgUsuario;
    private ForumPostStatus statusPost;
    private int curtidasCount;
    private boolean curtidoPorMim;
    private boolean emDestaque;
    private boolean destaqueManual;
    private boolean destaqueExcluido;
    private boolean podeEditar;
    private boolean podeExcluir;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private java.time.LocalDateTime createdAt;
}
