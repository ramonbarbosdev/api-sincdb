package com.api_sincdb.domain.operacao.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResumoDTO {
    private int totalQueries;
    private int totalCategorias;
    private int totalPerigosas;
    private int totalSelecionadas;

    private boolean possuiOperacoesPerigosas;
    private boolean podeExecutar;

    private String mensagem;

}
