package com.supermarketmanager.prices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.RetailerOffer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PosoKanei (posokanei.gov.gr), the Greek government price-comparison platform (~10k products, 10 chains, daily).
 * <p>
 * Undocumented API, contract taken from the open-source posokanei-mcp (MIT). NOT verified live: from the machines
 * this was built on the API answered HTTP 403 to every client. Requests are serialised, at least
 * {@code minIntervalMs} apart, and cached by {@link CachedSearch}.
 */
@Component
public class PosoKaneiProvider implements PriceProvider {
    private static final int PAGE_SIZE = 50;

    private final RestClient http;
    private final long minIntervalMs;
    private long lastRequestAt;

    public PosoKaneiProvider(@Value("${sm.prices.posokanei.base-url:https://api.posokanei.gov.gr}") String baseUrl,
                             @Value("${sm.prices.posokanei.min-interval-ms:300}") long minIntervalMs,
                             @Value("${sm.prices.posokanei.timeout-ms:15000}") long timeoutMs) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build());
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "supermarket-manager (personal use)").build();
        this.minIntervalMs = minIntervalMs;
    }

    @Override
    public String id() {
        return "posokanei";
    }

    @Override
    public String description() {
        return "PosoKanei (posokanei.gov.gr) - all major Greek chains, daily prices (unofficial API, unverified)";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawPrice(String retailer, @JsonProperty("retailer_display_name") String retailerDisplayName,
                    @JsonProperty("retailer_name") String retailerName, Double price,
                    @JsonProperty("price_normalized") Double priceNormalized,
                    @JsonProperty("is_discount") Boolean isDiscount, @JsonProperty("last_updated") String lastUpdated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawProduct(String id, String name, String brand, String barcode, String gtin, String unit,
                      @JsonProperty("unit_quantity") Double unitQuantity, @JsonProperty("private_label") Boolean privateLabel,
                      @JsonProperty("retailer_prices") List<RawPrice> retailerPrices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawSearch(List<RawProduct> products, @JsonProperty("has_next") Boolean hasNext) {
    }

    @Override
    public List<Product> search(String query, int pages) {
        var out = new ArrayList<Product>();
        for (int page = 1; page <= Math.max(1, pages); page++) {
            RawSearch res = post("/products/search", Map.of("title", query, "page", page, "page_size", PAGE_SIZE,
                    "sort_by", "name", "sort_order", "asc"));
            if (res == null || res.products() == null) break;
            res.products().forEach(p -> out.add(toProduct(p)));
            if (!Boolean.TRUE.equals(res.hasNext())) break;
        }
        return out;
    }

    private synchronized RawSearch post(String path, Object body) {
        long wait = lastRequestAt + minIntervalMs - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting to call PosoKanei", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
        return http.post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(body).retrieve()
                .onStatus(s -> s.isError(), (req, resp) -> {
                    String hint = resp.getStatusCode().value() == 429 ? " (rate limited - try later)"
                            : resp.getStatusCode().value() == 403 ? " (blocked - PosoKanei may refuse non-browser or non-Greek clients)" : "";
                    throw new IllegalStateException("PosoKanei " + path + " failed: HTTP " + resp.getStatusCode().value() + hint);
                })
                .body(RawSearch.class);
    }

    static Product toProduct(RawProduct raw) {
        var offers = new ArrayList<RetailerOffer>();
        if (raw.retailerPrices() != null) {
            for (RawPrice p : raw.retailerPrices()) {
                String retailer = p.retailer() != null ? p.retailer() : p.retailerDisplayName() != null ? p.retailerDisplayName() : p.retailerName();
                if (retailer == null || p.price() == null) continue;
                String name = p.retailerDisplayName() != null ? p.retailerDisplayName() : p.retailerName() != null ? p.retailerName() : retailer;
                offers.add(new RetailerOffer(retailer, name, p.price(), p.priceNormalized(), Boolean.TRUE.equals(p.isDiscount()),
                        p.lastUpdated(), null));
            }
        }
        return new Product("posokanei", String.valueOf(raw.id()), raw.name(), raw.brand(),
                raw.barcode() != null ? raw.barcode() : raw.gtin(), raw.unit(), raw.unitQuantity(),
                Boolean.TRUE.equals(raw.privateLabel()), offers);
    }
}
