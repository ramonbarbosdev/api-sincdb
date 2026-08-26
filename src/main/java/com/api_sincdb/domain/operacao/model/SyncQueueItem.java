package com.api_sincdb.domain.operacao.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.api_sincdb.enums.SyncQueueItemStatus;
import com.api_sincdb.enums.TipoOperacao;
import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = "sync_queue_item")
public class SyncQueueItem {

    @Id
    private String id;

    private String usuario;

    private TipoOperacao operacao;

    private String baseNome;

    private String schemaNome;

    private String tabela;

    private List<String> tabelas = new ArrayList<>();

    private String label;

    private SyncQueueItemStatus status = SyncQueueItemStatus.PENDING;

    private String errorMessage;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd/MM/yyyy HH:mm:ss")
    private LocalDateTime finishedAt;
}
