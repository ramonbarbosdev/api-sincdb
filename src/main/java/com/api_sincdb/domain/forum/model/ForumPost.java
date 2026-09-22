package com.api_sincdb.domain.forum.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.ForumPostStatus;
import com.api_sincdb.enums.TipoFeedback;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "forum_post")
public class ForumPost {

    @Id
    private String id;

    private TipoFeedback tipo;

    private String titulo;

    private String descricao;

    private String idUsuario;

    private String nomeUsuario;

    private ForumPostStatus statusPost = ForumPostStatus.ABERTO;

    private int curtidasCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime createdAt;
}
