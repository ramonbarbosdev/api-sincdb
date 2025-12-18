package com.api_sincdb.domain.operacao.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DDLItemDTO {
    private String id;
    private String objeto;
    private String tipo;
    private String sql;
    private boolean perigoso;
    private boolean executavel;
    private boolean selecionado;
    private List<String> avisos;
    private List<String> dependencias;
}
