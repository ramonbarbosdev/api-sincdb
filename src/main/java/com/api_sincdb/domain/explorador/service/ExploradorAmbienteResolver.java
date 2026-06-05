package com.api_sincdb.domain.explorador.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.api_sincdb.enums.TipoConexao;

@Component
public class ExploradorAmbienteResolver {

    public TipoConexao resolver(String ambiente) {
        if ("cloud".equalsIgnoreCase(ambiente)) {
            return TipoConexao.CLOUD;
        }
        if ("local".equalsIgnoreCase(ambiente)) {
            return TipoConexao.LOCAL;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ambiente deve ser cloud ou local");
    }
}
