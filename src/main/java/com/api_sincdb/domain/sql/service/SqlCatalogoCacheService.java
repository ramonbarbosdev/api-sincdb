package com.api_sincdb.domain.sql.service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.api_sincdb.domain.sql.dto.SqlCatalogoDTO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

@Service
public class SqlCatalogoCacheService {

    private final Cache<String, SqlCatalogoDTO> cache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(200)
            .build();

    public Optional<SqlCatalogoDTO> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, SqlCatalogoDTO value) {
        cache.put(key, value);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }
}
