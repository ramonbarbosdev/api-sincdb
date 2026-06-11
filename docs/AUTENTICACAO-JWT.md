# Autenticação por JWT (JSON Web Token)

## Visão Geral

A autenticação por JWT no projeto `api-sincdb` é um sistema baseado em tokens digitalmente assinados que permite autenticar usuários de forma stateless. Uma vez autenticado, o usuário recebe um token que deve ser enviado em requisições subsequentes para acessar recursos protegidos.

---

## Fluxo de Autenticação

```
┌─────────────────┐
│   Cliente       │
│   (Frontend)    │
└────────┬────────┘
         │
         │ POST /auth/login (CPF + Senha)
         ▼
┌─────────────────────────────────┐
│ AuthController                  │
│ └── AuthService.login()         │
└────────┬────────────────────────┘
         │
         │ Valida credenciais no diretório
         ▼
┌─────────────────────────────────┐
│ AuthDirectoryService            │
│ └── autenticarCredenciais()     │
└────────┬────────────────────────┘
         │
         │ Se válido
         ▼
┌─────────────────────────────────┐
│ JWTTokenAutenticacaoService     │
│ └── gerarTokenSemTenant()       │
└────────┬────────────────────────┘
         │
         │ Token gerado e assinado
         ▼
┌─────────────────────────────────┐
│ LoginResponseDTO                │
│ {                               │
│   token: "Bearer eyJhbGc..." │ 
│   tipo: "DEFAULT"             │
│   primeiroLogin: true/false   │
│   organizacoes: [...]         │
│ }                              │
└─────────────────────────────────┘
```

---

## 1. Fluxo de Login Inicial

### 1.1 Requisição de Login

O cliente envia suas credenciais para autenticação:

```bash
POST /auth/login
Content-Type: application/json

{
  "nuCpf": "12345678900",
  "dsSenha": "senha123"
}
```

### 1.2 Validação de Credenciais

1. **Endpoint**: `AuthController.login(LoginRequestDTO request)`
2. **Serviço**: `AuthService.login()` chama `AuthDirectoryService.autenticarCredenciais()`
3. **Validação**:
   - Verifica credenciais no banco de dados
   - Compara senha usando BCryptPasswordEncoder
   - Se inválido, lança exceção

### 1.3 Geração do Token Temporário

Após validar as credenciais, é gerado um token **sem tenant**:

```java
String token = jwtTokenAutenticacaoService.gerarTokenSemTenant(usuario, "DEFAULT");
```

**Claims do Token Temporário:**
```json
{
  "sub": "login_do_usuario",           // Subject (login)
  "id_usuario": "uuid_usuario",        // ID do usuário
  "tipoGlobal": "DEFAULT",             // Tipo global
  "exp": 1704067200,                   // Expiração em 48 horas
  "iat": 1703980800                    // Criado em
}
```

### 1.4 Resposta do Login

```json
{
  "token": "Bearer eyJhbGciOiJIUzUxMiJ9...",
  "tipo": "DEFAULT",
  "primeiroLogin": true,
  "listaOrganizacoes": [
    {
      "id": "uuid_empresa",
      "nome": "Empresa XYZ",
      "tenant": "uuid_tenant"
    }
  ]
}
```

---

## 2. Seleção de Organização

Após o login inicial, o usuário deve selecionar qual organização/empresa deseja acessar:

```bash
POST /auth/selecionar-organizacao
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
Content-Type: application/json

{
  "idOrganizacao": "uuid_empresa"
}
```

### 2.1 Validação e Geração do Token com Tenant

1. **Extração do ID do usuário do token**: 
   ```java
   String idUsuario = TenantRuntimeContext.getIdUsuario();
   ```

2. **Validação da empresa**:
   - Verifica se o usuário tem acesso à organização
   - Obtém os dados da empresa e tenant associado

3. **Geração do token com tenant**:
   ```java
   String token = jwtTokenAutenticacaoService.gerarTokenComTenant(
       usuario,
       empresa.getId(),           // ID da empresa
       empresa.getId_tenant(),    // ID do tenant
       dsRole,                    // Role (ADMIN, USER, etc)
       permissoes                 // Lista de permissões
   );
   ```

### 2.2 Token com Tenant

**Claims do Token com Tenant:**
```json
{
  "sub": "login_do_usuario",
  "id_usuario": "uuid_usuario",
  "id_empresa": "uuid_empresa",
  "id_tenant": "uuid_tenant",
  "idOrganizacao": "uuid_empresa",
  "tipoGlobal": "DEFAULT",
  "role": "ADMIN",
  "permissoes": ["READ", "WRITE"],
  "exp": 1704067200,
  "iat": 1703980800
}
```

### 2.3 Resposta de Seleção de Organização

```json
{
  "token": "Bearer eyJhbGciOiJIUzUxMiJ9...",
  "idOrganizacao": "uuid_empresa",
  "role": "ADMIN",
  "permissoes": ["READ", "WRITE"]
}
```

---

## 3. Estrutura do JWT

### 3.1 Componentes do Token

Um JWT é composto por 3 partes separadas por ponto (`.`):

```
Header.Payload.Signature
```

**Exemplo decodificado:**
```
Header:
{
  "alg": "HS512",
  "typ": "JWT"
}

Payload:
{
  "sub": "usuario_login",
  "id_usuario": "uuid",
  "id_empresa": "uuid",
  "id_tenant": "uuid",
  "exp": 1704067200,
  "iat": 1703980800
}

Signature:
HmacSHA512(base64UrlEncode(header) + "." + base64UrlEncode(payload), SECRET_KEY)
```

### 3.2 Algoritmo de Assinatura

- **Algoritmo**: `HS512` (HMAC SHA-512)
- **Chave Secreta**: Base64 armazenada em `JWTTokenAutenticacaoService.SECRET_KEY_BASE64`
- **Tamanho**: 512 bits

---

## 4. Processamento do Token nas Requisições

### 4.1 Filter de Validação

Todo request passa pelo filtro `JwtApiAutenticacaoFilter` que:

1. **Extrai o token** do header ou cookie:
   ```java
   String token = jwtService.obterTokenHeaderOuCookie(request);
   ```

2. **Valida a assinatura**:
   - Decodifica o token
   - Verifica se foi assinado com a chave secreta correta

3. **Extrai claims**:
   ```java
   String idTenant = jwtService.extractTenantId(token);
   String idEmpresa = jwtService.extractEmpresaId(token);
   String idUsuario = jwtService.extractLogin(token);
   String login = jwtService.extractSubject(token);
   ```

4. **Configura o contexto de tenant**:
   ```java
   TenantContext.setTenantId(idTenant);
   TenantRuntimeContext.set(idUsuario, idEmpresa, idTenant, login);
   ```

5. **Cria Authentication**:
   ```java
   Authentication authentication = jwtService.getAuthentication(httpRequest, response);
   SecurityContextHolder.getContext().setAuthentication(authentication);
   ```

### 4.2 Fluxo de Validação do Token

```
┌─────────────────────────────────┐
│ Requisição com Token            │
│ Authorization: Bearer ...       │
└────────┬────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│ JwtApiAutenticacaoFilter        │
│ .doFilter()                     │
└────────┬────────────────────────┘
         │
         ├─ Extrai token do header ou cookie
         │
         ├─ Valida assinatura (HS512)
         │  ├─ Se expirado: 401 Unauthorized
         │  ├─ Se malformado: 401 Unauthorized
         │  └─ Se inválido: 401 Unauthorized
         │
         ├─ Extrai claims (id_usuario, id_empresa, id_tenant, etc)
         │
         ├─ Valida acesso ao tenant
         │  └─ Verifica se usuario tem acesso à empresa/tenant
         │
         ├─ Carrega usuário do banco de dados
         │
         ├─ Configura SecurityContext
         │  └─ SecurityContextHolder.getContext().setAuthentication()
         │
         ├─ Configura TenantContext
         │  └─ TenantRuntimeContext.set()
         │
         └─ Prossegue para o controller
            └─ @Secured, @PreAuthorize, etc
```

### 4.3 Tratamento de Erros

| Erro | Status | Resposta |
|------|--------|----------|
| Token Expirado | 401 | `{"error": "Token expirado."}` |
| Token Malformado | 401 | `{"error": "Token malformado."}` |
| Token Inválido | 401 | `{"error": "Erro na autenticação: ..."}` |
| Sem Token | 401 | Sem resposta (retorna null) |

---

## 5. Armazenamento e Transmissão do Token

### 5.1 Transmissão

O token pode ser enviado de duas formas:

**1. Header Authorization**
```
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**2. Cookie HTTP** (prioridade)
```
Cookie: access_token=eyJhbGciOiJIUzUxMiJ9...
```

O filtro verifica o cookie primeiro antes do header.

### 5.2 Criação de Cookie

O cookie é criado no método `inserirJwtCookie()`:

```java
Set-Cookie: access_token=eyJhbGciOiJIUzUxMiJ9...; 
            Path=/; 
            HttpOnly; 
            Secure; 
            SameSite=None; 
            Max-Age=3600
```

**Propriedades:**
- **HttpOnly**: Protege contra ataques XSS (não acessível via JavaScript)
- **Secure**: Apenas enviado em conexões HTTPS
- **SameSite=None**: Permite requisições cross-site (com Secure)
- **Max-Age**: Expiração em 3600 segundos (1 hora)

---

## 6. Expiração do Token

### 6.1 Tempo de Expiração

```java
private static final long EXPIRATION_TIME = 172800000; // 48 horas em ms
```

**Cálculo:**
```
EXPIRATION_TIME = 172800000 ms = 48 horas
Date expiração = System.currentTimeMillis() + 172800000
```

### 6.2 Tratamento de Token Expirado

Quando o token expira:

```java
catch (ExpiredJwtException e) {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
    response.getWriter().write("{\"error\": \"Token expirado.\"}");
}
```

**Fluxo de renovação:**
1. Cliente recebe 401
2. Cliente limpa o token expirado
3. Cliente redireciona para login
4. Novo login gera novo token

---

## 7. Logout

O logout é realizado pelo método `AuthService.logout()`:

```bash
POST /auth/logout
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Ações:**
1. Remove o cookie do cliente:
   ```java
   ResponseCookie cookie = ResponseCookie.from("access_token", "")
       .maxAge(0)  // Expira imediatamente
       .httpOnly(true)
       .secure(true)
       .sameSite("None")
       .path(CHAVE_COOKIE)
       .build();
   ```

2. Limpa o contexto de segurança:
   ```java
   new SecurityContextLogoutHandler()
       .logout(request, response, SecurityContextHolder.getContext().getAuthentication());
   ```

---

## 8. Contexto de Tenant (Multi-Tenancy)

### 8.1 Isolamento de Dados

O sistema usa `TenantContext` e `TenantRuntimeContext` para isolamento multi-tenant:

```java
// Definido no filtro JWT
TenantContext.setTenantId(idTenant);
TenantRuntimeContext.set(idUsuario, idEmpresa, idTenant, login);
```

### 8.2 Acesso aos Dados de Tenant

Em qualquer parte do código:

```java
String idTenant = TenantContext.getTenantId();
String idEmpresa = TenantRuntimeContext.getIdEmpresa();
String idUsuario = TenantRuntimeContext.getIdUsuario();
String login = TenantRuntimeContext.getLogin();
```

### 8.3 Limpeza de Contexto

Após cada requisição, o contexto é limpo:

```java
finally {
    TenantContext.clear();
    TenantRuntimeContext.clear();
}
```

---

## 9. Configuração de Segurança

### 9.1 WebConfigSecurity

```java
@Configuration
@EnableWebSecurity
public class WebConfigSecurity {
    
    // Endpoints públicos (sem autenticação)
    .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
    .requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
    .requestMatchers(HttpMethod.POST, "/auth/logout").permitAll()
    .requestMatchers(HttpMethod.GET, "/status/").permitAll()
    .requestMatchers("/swagger-ui/**").permitAll()
    .requestMatchers("/v3/api-docs/**").permitAll()
    
    // Todos outros endpoints requerem autenticação
    .anyRequest().authenticated()
    
    // Adiciona o filtro JWT antes do UsernamePasswordAuthenticationFilter
    .addFilterBefore(new JwtApiAutenticacaoFilter(), UsernamePasswordAuthenticationFilter.class)
}
```

### 9.2 CORS (Cross-Origin Resource Sharing)

```java
CorsConfiguration configuration = new CorsConfiguration();
configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:*"));
configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
configuration.setAllowedHeaders(Arrays.asList("*"));
configuration.setAllowCredentials(true);
```

---

## 10. Segurança de Senha

### 10.1 Hashing de Senha

As senhas são armazenadas com hash BCrypt:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Processo:**
1. Usuário envia senha em texto plano
2. BCrypt gera hash + salt
3. Hash é comparado durante login
4. Senha nunca é armazenada em texto plano

---

## 11. Exemplo Completo de Fluxo

### Passo 1: Login
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "nuCpf": "12345678900",
    "dsSenha": "senha123"
  }'
```

**Resposta:**
```json
{
  "token": "Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "tipo": "DEFAULT",
  "primeiroLogin": true,
  "listaOrganizacoes": [
    {
      "id": "org-123",
      "nome": "Minha Empresa",
      "tenant": "tenant-456"
    }
  ]
}
```

### Passo 2: Selecionar Organização
```bash
curl -X POST http://localhost:8080/auth/selecionar-organizacao \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..." \
  -H "Content-Type: application/json" \
  -d '{
    "idOrganizacao": "org-123"
  }'
```

**Resposta:**
```json
{
  "token": "Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9...",
  "idOrganizacao": "org-123",
  "role": "ADMIN",
  "permissoes": ["READ", "WRITE"]
}
```

### Passo 3: Usar Token em Requisições Autenticadas
```bash
curl -X GET http://localhost:8080/api/recurso \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
```

### Passo 4: Logout
```bash
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9..."
```

---

## 12. Dicas de Segurança

### ✅ Boas Práticas Implementadas

1. ✅ Token assinado com chave secreta forte (HS512)
2. ✅ Senha hasheada com BCrypt (não texto plano)
3. ✅ Token armazenado em cookie HttpOnly (protege contra XSS)
4. ✅ Cookie com Secure (apenas HTTPS)
5. ✅ Validação de tenant no token
6. ✅ Contexto limpo após cada requisição
7. ✅ Tratamento de erros com status HTTP correto

### ⚠️ Pontos de Atenção

1. ⚠️ Chave secreta está hardcoded no código (considere usar variáveis de ambiente)
2. ⚠️ CORS configurado para `http://localhost:*` (ajustar em produção)
3. ⚠️ Token expira em 48 horas (considere rotação automática)
4. ⚠️ Sem mecanismo de revogação de token (token blacklist)

### 🛡️ Recomendações para Produção

```java
// ✅ Armazenar chave em variável de ambiente
@Value("${jwt.secret}")
private String SECRET_KEY_BASE64;

// ✅ Usar HTTPS obrigatoriamente
configuration.setAllowedOrigins(Arrays.asList("https://seu-dominio.com"));

// ✅ Implementar refresh token
// Token de curta duração + refresh token de longa duração

// ✅ Implementar token blacklist
// Manter lista de tokens revogados/feitos logout

// ✅ Monitorar tentativas de login falhadas
// Rate limiting, bloqueio de conta após N tentativas
```

---

## 13. Endpoints de Autenticação

| Método | Endpoint | Autenticação | Descrição |
|--------|----------|--------------|-----------|
| POST | `/auth/login` | ❌ Não | Login com CPF e senha |
| POST | `/auth/register` | ❌ Não | Registrar novo usuário |
| POST | `/auth/selecionar-organizacao` | ✅ Sim | Selecionar organização |
| POST | `/auth/logout` | ✅ Sim | Fazer logout |
| GET | `/auth/me` | ✅ Sim | Obter dados do usuário logado |

---

## 14. Referências

- **JJWT (JWT Library)**: https://github.com/jwtk/jjwt
- **RFC 7519 (JWT)**: https://tools.ietf.org/html/rfc7519
- **OWASP JWT**: https://owasp.org/www-community/attacks/JWT_Token_Confusion
- **Spring Security**: https://docs.spring.io/spring-security/

---

**Última atualização**: 2026-06-11
