package com.api_sincdb.domain.feedback.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.TipoFeedback;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "feedback_report")
public class FeedbackReport {

    @Id
    private String id;

    private TipoFeedback tipo;

    private String titulo;

    private String descricao;

    private String usuario;

    /** Nome de exibição (campo nome do cadastro). */
    private String nomeUsuario;

    private String idEmpresa;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime createdAt;
}
