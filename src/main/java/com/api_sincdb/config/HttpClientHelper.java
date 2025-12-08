package com.api_sincdb.config;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpClientHelper {
    
      private final RestTemplate restTemplate = new RestTemplate();

       /**
     * Executa requisições HTTP genéricas (GET, POST, PUT, DELETE).
     *
     * @param url       URL completa do endpoint
     * @param method    Método HTTP (GET, POST, PUT, DELETE)
     * @param body      Corpo da requisição (pode ser null para GET/DELETE)
     * @param token     Token de autenticação (pode ser null)
     * @param responseType Classe esperada de resposta (ex: Map.class, String.class)
     * @return ResponseEntity<T> com o corpo e status da resposta
     *
     * @author Ramon
     * @since 2025-11-11
     */
    public <T> ResponseEntity<T> request(
            String url,
            HttpMethod method,
            Object body,
            String token,
            Class<T> responseType) {

        HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (token != null && !token.isEmpty()) {
                headers.set("Authorization", "Bearer " + token);
            }

            HttpEntity<Object> entity = new HttpEntity<>(body, headers);

            return restTemplate.exchange(url, method, entity, responseType);
    }
}
