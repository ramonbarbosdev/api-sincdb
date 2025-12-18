package com.api_sincdb.domain.operacao.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class CategoriaDDLDTO {

    private String id;
    private String titulo;
    private String icone;
    private int ordem;
    private boolean perigosa;
    private int total;
    private List<DDLItemDTO> items;
}
