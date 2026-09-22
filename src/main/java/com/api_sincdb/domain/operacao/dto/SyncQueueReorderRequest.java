package com.api_sincdb.domain.operacao.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncQueueReorderRequest {

    private List<String> orderedIds = new ArrayList<>();
}
