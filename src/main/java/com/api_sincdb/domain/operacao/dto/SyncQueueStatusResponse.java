package com.api_sincdb.domain.operacao.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SyncQueueStatusResponse {

    private final boolean running;
    private final int pendingCount;
    private final String currentItemId;
}
