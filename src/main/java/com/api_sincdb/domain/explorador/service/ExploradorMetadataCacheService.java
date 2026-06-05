package com.api_sincdb.domain.explorador.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

@Service
public class ExploradorMetadataCacheService {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, CacheEntry<?>> cache = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Supplier<T> supplier) {
        CacheEntry<?> entry = cache.get(key);

        if (entry != null && !entry.expirado()) {
            return (T) entry.valor();
        }

        T valor = supplier.get();
        cache.put(key, new CacheEntry<>(valor, Instant.now().plus(TTL)));
        return valor;
    }

    public void evictByPrefix(String prefix) {
        cache.keySet().removeIf(key -> key.startsWith(prefix));
    }

    private record CacheEntry<T>(T valor, Instant expiresAt) {

        boolean expirado() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
