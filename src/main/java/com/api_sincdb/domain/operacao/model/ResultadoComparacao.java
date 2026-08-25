package com.api_sincdb.domain.operacao.model;

import java.util.ArrayList;
import java.util.List;

public class ResultadoComparacao {
   
    private final List<String> alteracoes = new ArrayList<>();
    private final List<String> colunasAlteradas = new ArrayList<>();
    private final List<String> colunasNovas = new ArrayList<>();
    private final List<String> colunasRemovidas = new ArrayList<>();
    private final List<ColunaPendenteNotNull> colunasPendenteNotNull = new ArrayList<>();

    public List<String> getAlteracoes() {
        return alteracoes;
    }

    public List<String> getColunasAlteradas() {
        return colunasAlteradas;
    }

    public List<String> getColunasNovas() {
        return colunasNovas;
    }

    public List<String> getColunasRemovidas() {
        return colunasRemovidas;
    }

    public List<ColunaPendenteNotNull> getColunasPendenteNotNull() {
        return colunasPendenteNotNull;
    }

    public void adicionarPendenteNotNull(String tabela, String coluna) {
        colunasPendenteNotNull.add(new ColunaPendenteNotNull(tabela, coluna));
    }

    public boolean hasChanges() {
        return !alteracoes.isEmpty() || !colunasPendenteNotNull.isEmpty();
    }
}
