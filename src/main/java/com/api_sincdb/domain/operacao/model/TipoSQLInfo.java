package com.api_sincdb.domain.operacao.model;

public class TipoSQLInfo {
     private String tipo;
    private boolean usaTamanho;
    private boolean usaPrecisaoEscala;

    public TipoSQLInfo(String tipo, boolean usaTamanho, boolean usaPrecisaoEscala) {
        this.tipo = tipo;
        this.usaTamanho = usaTamanho;
        this.usaPrecisaoEscala = usaPrecisaoEscala;
    }

    public String getTipo() {
        return tipo;
    }

    public boolean isUsaTamanho() {
        return usaTamanho;
    }

    public boolean isUsaPrecisaoEscala() {
        return usaPrecisaoEscala;
    }
}
