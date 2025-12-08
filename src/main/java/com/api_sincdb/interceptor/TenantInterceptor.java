package com.api_sincdb.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.api_sincdb.context.TenantContext;
import com.api_sincdb.security.JWTTokenAutenticacaoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final JWTTokenAutenticacaoService jwtService;

    public TenantInterceptor(JWTTokenAutenticacaoService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isEmpty()) {
            String tenantId = jwtService.extractTenantId(authHeader);
            TenantContext.setTenantId(tenantId);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}