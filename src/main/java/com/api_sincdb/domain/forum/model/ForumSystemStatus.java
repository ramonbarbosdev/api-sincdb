package com.api_sincdb.domain.forum.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.ForumEstadoGeral;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "forum_system_status")
public class ForumSystemStatus {

    public static final String GLOBAL_ID = "global";

    @Id
    private String id = GLOBAL_ID;

    private ForumEstadoGeral estadoGeral = ForumEstadoGeral.OPERACIONAL;

    private String titulo = "SyncDB operacional";

    private String mensagem = "Todos os serviços estão funcionando normalmente.";

    private String proximaVersao;

    private String previsaoRelease;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime atualizadoEm;

    private String atualizadoPor;
}
