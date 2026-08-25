package com.api_sincdb.domain.operacao.model;

public class ColunaPendenteNotNull {

    private final String tabela;
    private final String coluna;

    public ColunaPendenteNotNull(String tabela, String coluna) {
        this.tabela = tabela;
        this.coluna = coluna;
    }

    public String getTabela() {
        return tabela;
    }

    public String getColuna() {
        return coluna;
    }

    public String toMarcadorCache() {
        return tabela + "|" + coluna;
    }
}
