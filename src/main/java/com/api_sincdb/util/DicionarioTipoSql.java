package com.api_sincdb.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.api_sincdb.domain.operacao.model.TipoSQLInfo;


public class DicionarioTipoSql {

    private static final Map<String, TipoSQLInfo> tipos = new HashMap<>();

    static {
        tipos.put("VARCHAR", new TipoSQLInfo("varchar", true, false));
        tipos.put("CHAR", new TipoSQLInfo("char", true, false));
        tipos.put("TEXT", new TipoSQLInfo("text", false, false));
        tipos.put("BPCHAR", new TipoSQLInfo("varchar", true, false));

        tipos.put("INT", new TipoSQLInfo("integer", false, false));
        tipos.put("INTEGER", new TipoSQLInfo("integer", false, false));
        tipos.put("BIGINT", new TipoSQLInfo("bigint", false, false));

        tipos.put("NUMERIC", new TipoSQLInfo("numeric", false, true));
        tipos.put("DECIMAL", new TipoSQLInfo("numeric", false, true));
        tipos.put("FLOAT", new TipoSQLInfo("float", false, false));
        tipos.put("DOUBLE", new TipoSQLInfo("double precision", false, false));

        tipos.put("DATE", new TipoSQLInfo("date", false, false));
        tipos.put("TIMESTAMP", new TipoSQLInfo("timestamp", false, false));
        tipos.put("BOOLEAN", new TipoSQLInfo("boolean", false, false));

        // tipos inválidos ou exagerados podem ser tratados assim
        tipos.put("CLOB", new TipoSQLInfo("text", false, false)); // evita varchar gigante
        tipos.put("LONGVARCHAR", new TipoSQLInfo("text", false, false));

        tipos.put("BIGSERIAL", new TipoSQLInfo("bigint", false, false));
        tipos.put("SERIAL", new TipoSQLInfo("serial", false, false));
    }

    public static TipoSQLInfo getTipo(String tipoOriginal) {
        TipoSQLInfo tipo = tipos.get(tipoOriginal.toUpperCase());
        return tipo != null ? tipo : new TipoSQLInfo(tipoOriginal.toLowerCase(), false, false);
    }
}