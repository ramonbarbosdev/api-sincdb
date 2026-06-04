package com.api_sincdb.security;

import java.io.IOException;
import java.security.Principal;
import java.security.SignatureException;
import java.util.List;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.api_sincdb.ApplicationContextLoad;
import com.api_sincdb.domain.empresa.model.Empresa;
import com.api_sincdb.domain.empresa.repository.EmpresaRepository;
import com.api_sincdb.domain.empresa.repository.UsuarioEmpresaRepository;
import com.api_sincdb.domain.usuario.model.Usuario;
import com.api_sincdb.domain.usuario.repository.UsuarioRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKey;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;

@Service
public class JWTTokenAutenticacaoService {

    private static final long EXPIRATION_TIME = 172800000;
    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HEADER_STRING = "Authorization";

    @Value("${server.servlet.context-path}")
    private String CHAVE_COOKIE;

    private static final String SECRET_KEY_BASE64 = "HaqrDaAaICtFZNXjm5Q3dPNgAZX+bnf6efMy2HuIO1Iq928rcmtTltoAFhsROHxNwtcHjB6FWudgjqxBMXAP8w==";

    public static SecretKeySpec createSecretKey() {
        String cleanedKey = SECRET_KEY_BASE64.replaceAll("\\s", "");
        byte[] decodedKey = java.util.Base64.getDecoder().decode(cleanedKey);
        return new SecretKeySpec(decodedKey, "HmacSHA512");
    }

    public String addAuthentication(HttpServletResponse response, String username, String idTenant, String idEmpresa)
            throws Exception {
        SecretKeySpec secretKey = createSecretKey();

        Usuario usuario = ApplicationContextLoad.getApplicationContext()
                .getBean(UsuarioRepository.class)
                .findByLogin(username);

        String jwt = Jwts.builder()
                .setSubject(username)
                .claim("id_usuario", usuario.getId())
                .claim("id_empresa", idEmpresa)
                .claim("id_tenant", idTenant)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        String token = TOKEN_PREFIX + jwt;
        response.addHeader(HEADER_STRING, token);

        inserirJwtCookie(jwt, response);
        liberacaoCors(response);

        return token;
    }

    public String addAuthenticationSemTenant(String username) throws Exception {
        SecretKeySpec secretKey = createSecretKey();

        Usuario usuario = ApplicationContextLoad.getApplicationContext()
                .getBean(UsuarioRepository.class)
                .findByLogin(username);

        String jwt = Jwts.builder()
                .setSubject(username)
                .claim("id_usuario", usuario.getId())
                .claim("tipoGlobal", "DEFAULT")
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        return jwt;
    }

    public String gerarTokenSemTenant(Usuario usuario, String tipoGlobal) {
        SecretKeySpec secretKey = createSecretKey();

        String jwt = Jwts.builder()
                .setSubject(usuario.getLogin())
                .claim("id_usuario", usuario.getId())
                .claim("tipoGlobal", tipoGlobal)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        return jwt;
    }

    public String gerarTokenComTenant(Usuario usuario, String idOrganizacao, String idTenant, String role,
            List<String> permissoes) {
        SecretKeySpec secretKey = createSecretKey();

        String jwt = Jwts.builder()
                .setSubject(usuario.getLogin())
                .claim("id_usuario", usuario.getId())
                .claim("id_empresa", idOrganizacao)
                .claim("id_tenant", idTenant)
                .claim("idOrganizacao", idOrganizacao)
                .claim("tipoGlobal", "DEFAULT")
                .claim("role", role)
                .claim("permissoes", permissoes)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        return jwt;
    }

    public String obterUsuarioLogado(String token) {

        if (token.isEmpty()) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return (String) auth.getName();

        } else {
            SecretKeySpec secretKey = createSecretKey();
            String jwt = token.replace(TOKEN_PREFIX, "").trim();

            String user = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody()
                    .getSubject();

            return user;
        }

    }

    public String extractTenantId(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("id_tenant", String.class);
    }

    public String extractEmpresaId(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("id_empresa", String.class);
    }

    public String extractTipoGlobal(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("tipoGlobal", String.class);
    }

    public String extractRole(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("role", String.class);
    }

    public List<String> extractPermissoes(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("permissoes", List.class);
    }

    public String extractSubject(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String extractLogin(String token) {
        SecretKeySpec secretKey = createSecretKey();

        if (token.startsWith(TOKEN_PREFIX)) {
            token = token.replace(TOKEN_PREFIX, "").trim();
        }

        String response = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .get("id_usuario", String.class);

        return response;
    }

       private String obterTokenCookie(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public String obterTokenHeaderOuCookie(HttpServletRequest request) {
        String cookieToken = obterTokenCookie(request);
        if (cookieToken != null && !cookieToken.isEmpty()) {
            return TOKEN_PREFIX + cookieToken;
        }
        return request.getHeader(HEADER_STRING);
    }

        public void inserirJwtCookie(String jwt, HttpServletResponse response) {
        StringBuilder cookieValue = new StringBuilder();
        cookieValue.append("access_token=").append(jwt)
                .append("; Path=").append(CHAVE_COOKIE)
                .append("; HttpOnly")
                .append("; Secure")
                .append("; SameSite=None")
                .append("; Max-Age=3600"); // 1 hora

        response.addHeader("Set-Cookie", cookieValue.toString());
    }

    public void removerJwtCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .maxAge(0)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path(CHAVE_COOKIE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }


    public Authentication getAuthentication(HttpServletRequest request, HttpServletResponse response) {
        SecretKeySpec secretKey = createSecretKey();
        String token = obterTokenHeaderOuCookie(request);

        if (token != null && token.startsWith(TOKEN_PREFIX)) {
            String jwt = token.replace(TOKEN_PREFIX, "").trim();

            try {
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(secretKey)
                        .build()
                        .parseClaimsJws(jwt)
                        .getBody();

                String user = claims.getSubject();
                String idUsuario = claims.get("id_usuario", String.class);
                String idEmpresa = claims.get("id_empresa", String.class);
                String idTenant = claims.get("id_tenant", String.class);

                if (user != null) {
                    Usuario usuario = ApplicationContextLoad.getApplicationContext()
                            .getBean(UsuarioRepository.class)
                            .findByLogin(user);

                    if (usuario != null && usuario.getId().equals(idUsuario)
                            && validarAcessoTenant(usuario, idEmpresa, idTenant)) {
                        return new UsernamePasswordAuthenticationToken(
                                usuario.getLogin(),
                                usuario.getSenha(),
                                usuario.getAuthorities());
                    }
                }

            } catch (ExpiredJwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\": \"Token expirado.\"}");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (MalformedJwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\": \"Token malformado.\"}");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                try {
                    response.getWriter().write("{\"error\": \"Erro na autenticação: " + e.getMessage() + "\"}");
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }

        }

        liberacaoCors(response);
        return null;
    }

    private boolean validarAcessoTenant(Usuario usuario, String idEmpresa, String idTenant) {
        if (idEmpresa == null || idEmpresa.isBlank() || idTenant == null || idTenant.isBlank()) {
            return true;
        }

        Empresa empresa = ApplicationContextLoad.getApplicationContext()
                .getBean(EmpresaRepository.class)
                .findById(idEmpresa)
                .orElse(null);

        if (empresa == null || !empresa.isFl_ativo() || !idTenant.equals(empresa.getId_tenant())) {
            return false;
        }

        return ApplicationContextLoad.getApplicationContext()
                .getBean(UsuarioEmpresaRepository.class)
                .existsById_usuarioAndId_empresa(usuario.getId(), idEmpresa);
    }

    private void liberacaoCors(HttpServletResponse response) {
        if (response.getHeader("Access-Control-Allow-Origin") == null) {
            response.addHeader("Access-Control-Allow-Origin", "*");
        }

        if (response.getHeader("Access-Control-Allow-Headers") == null) {
            response.addHeader("Access-Control-Allow-Headers", "*");
        }

        if (response.getHeader("Access-Control-Request-Headers") == null) {
            response.addHeader("Access-Control-Request-Headers", "*");
        }

        if (response.getHeader("Access-Control-Allow-Methods") == null) {
            response.addHeader("Access-Control-Allow-Methods", "*");
        }
    }
}
