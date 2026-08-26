package com.api_sincdb.domain.operacao.dto;

import java.util.ArrayList;
import java.util.List;

import com.api_sincdb.enums.TipoOperacao;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SyncQueueEnqueueRequest {

    private TipoOperacao operacao;

    private String base;

    private String esquema;

    private String tabela;

    private List<String> tabelas = new ArrayList<>();

    private String label;
}
