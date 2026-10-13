package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.Product;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small in-memory cache in front of adapters (independent of the price-comparison module's SQLite cache). */
@Component
public class SupermarketSearch {
    private record Entry(long at, List<Product> products) {
    }

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final long ttlMs;
    private final int pages;

    public SupermarketSearch(@Value("${sm.supermarkets.cache-ttl-minutes:60}") long ttlMinutes,
                             @Value("${sm.supermarkets.search-pages:1}") int pages) {
        this.ttlMs = ttlMinutes * 60_000;
        this.pages = pages;
    }

    public List<Product> search(SupermarketAdapter adapter, String query) {
        String key = adapter.id() + "|" + query;
        Entry e = cache.get(key);
        if (e != null && System.currentTimeMillis() - e.at() < ttlMs) return e.products();
        List<Product> fresh = adapter.search(query, pages);
        cache.put(key, new Entry(System.currentTimeMillis(), fresh));
        return fresh;
    }
}
