package com.api_sincdb.helper;


import org.springframework.stereotype.Component;

import com.api_sincdb.security.JWTTokenAutenticacaoService;

@Component
public class JwtHelper {

    private final JWTTokenAutenticacaoService jwtService;

    public JwtHelper(JWTTokenAutenticacaoService jwtService) {
        this.jwtService = jwtService;
    }

    public String extrairUsuario(String token) {
        return jwtService.extractLogin(token);
    }
}