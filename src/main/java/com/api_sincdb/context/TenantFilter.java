package com.api_sincdb.context;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;

// @Component
public class TenantFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String tenantId = req.getHeader("X-Tenant-ID");

        // if (tenantId != null && !tenantId.isEmpty()) {
        //     TenantContext.setTenantId(tenantId);
        // }

        // try {
        //     chain.doFilter(request, response);
        // } finally {
        //     TenantContext.clear(); // limpa o contexto do tenant após a requisição
        // }
    }
}
