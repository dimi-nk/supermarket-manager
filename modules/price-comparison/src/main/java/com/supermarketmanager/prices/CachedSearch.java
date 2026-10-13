package com.supermarketmanager.prices;

import com.supermarketmanager.core.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** SQLite-backed search cache so repeated plans don't hammer public APIs. Shared by every provider and supermarket adapter. */
@Component
public class CachedSearch {
    private static final TypeReference<List<Product>> PRODUCTS = new TypeReference<>() {
    };
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final long ttlMs;

    public CachedSearch(JdbcClient jdbc, ObjectMapper json, @Value("${sm.prices.cache-ttl-minutes:360}") long ttlMinutes) {
        this.jdbc = jdbc;
        this.json = json;
        this.ttlMs = ttlMinutes * 60_000;
    }

    public interface Fetch {
        List<Product> get();
    }

    public List<Product> search(String provider, String query, int pages, Fetch fetch) {
        if (ttlMs <= 0) return fetch.get();
        String key = sha1(provider + "|" + pages + "|" + query);
        Optional<String> cached = jdbc.sql("SELECT payload FROM prices_cache WHERE cache_key = ? AND fetched_at > ?")
                .params(key, System.currentTimeMillis() - ttlMs).query(String.class).optional();
        if (cached.isPresent()) {
            try {
                return json.readValue(cached.get(), PRODUCTS);
            } catch (RuntimeException ignored) {
                // unreadable cache entry: refetch
            }
        }
        List<Product> fresh = fetch.get();
        jdbc.sql("""
                INSERT INTO prices_cache (cache_key, provider, query, fetched_at, payload) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE SET fetched_at = excluded.fetched_at, payload = excluded.payload""")
                .params(key, provider, query, System.currentTimeMillis(), json.writeValueAsString(fresh)).update();
        return fresh;
    }

    public int clear() {
        return jdbc.sql("DELETE FROM prices_cache").update();
    }

    private static String sha1(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
