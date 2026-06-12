package com.api_sincdb.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import com.api_sincdb.context.TenantContext;
import com.api_sincdb.context.TenantRuntimeContext;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtApiAutenticacaoFilter extends GenericFilterBean {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        JWTTokenAutenticacaoService jwtService = new JWTTokenAutenticacaoService();
        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                Authentication authentication = jwtService.getAuthentication(httpRequest, (HttpServletResponse) response);

                if (authentication != null) {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }

            preencherTenantRuntime(jwtService, httpRequest);

            if (tokenTemporarioEmRotaProtegida(httpRequest)) {
                escreverErroSelecaoOrganizacaoObrigatoria((HttpServletResponse) response);
                return;
            }

            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            TenantRuntimeContext.clear();
        }
    }

    private void preencherTenantRuntime(JWTTokenAutenticacaoService jwtService, HttpServletRequest request) {
        String token = jwtService.obterTokenHeaderOuCookie(request);

        if (token == null || token.isBlank()) {
            return;
        }

        try {
            String idTenant = jwtService.extractTenantId(token);
            String idEmpresa = jwtService.extractEmpresaId(token);
            String idUsuario = jwtService.extractLogin(token);
            String login = jwtService.extractSubject(token);

            TenantContext.setTenantId(idTenant);
            TenantRuntimeContext.set(idUsuario, idEmpresa, idTenant, login);
        } catch (Exception ignored) {
            TenantContext.clear();
            TenantRuntimeContext.clear();
        }
    }

    private boolean tokenTemporarioEmRotaProtegida(HttpServletRequest request) {
        String idUsuario = TenantRuntimeContext.getIdUsuario();
        String idEmpresa = TenantRuntimeContext.getIdEmpresa();
        String idTenant = TenantRuntimeContext.getIdTenant();

        if (idUsuario == null || idUsuario.isBlank()) {
            return false;
        }

        if (idEmpresa != null && !idEmpresa.isBlank() && idTenant != null && !idTenant.isBlank()) {
            return false;
        }

        return !rotaPermitidaComTokenTemporario(request);
    }

    private boolean rotaPermitidaComTokenTemporario(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        return "POST".equalsIgnoreCase(method) && "/auth/selecionar-organizacao".equals(path);
    }

    private void escreverErroSelecaoOrganizacaoObrigatoria(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"message\":\"Selecione uma organizacao antes de acessar este recurso.\",\"status\":403}");
    }
}
