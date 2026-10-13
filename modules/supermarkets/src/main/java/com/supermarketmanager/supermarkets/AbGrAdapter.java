package com.supermarketmanager.supermarkets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.RetailerOffer;
import com.supermarketmanager.core.text.Text;
import com.supermarketmanager.core.text.Units;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AB Vassilopoulos (ab.gr). Uses the GraphQL endpoint behind the e-shop (Ahold Delhaize platform):
 * {@code POST https://www.ab.gr/api/v1/} with an ad-hoc {@code productSearch} query. Read-only, no login.
 * Verified live on 2026-10-13. Unofficial: may change or be blocked without notice.
 * <p>
 * AB's own {@code price.unitPrice} repeats the pack price, so it is ignored; pack size comes from
 * {@code supplementaryPriceLabel2} ("600 γρ", "1 κιλ") or the product name.
 */
@Component
public class AbGrAdapter implements SupermarketAdapter {
    static final String QUERY = "query GetProductSearch($lang:String,$searchQuery:String,$pageNumber:Int,$pageSize:Int)"
            + "{productSearch(lang:$lang,searchQuery:$searchQuery,pageNumber:$pageNumber,pageSize:$pageSize)"
            + "{products{code name manufacturerName url price{value unitPrice supplementaryPriceLabel2 wasPrice showStrikethroughPrice}"
            + " stock{inStock} potentialPromotions{code description}} pagination{totalResults totalPages}}}";
    private static final int PAGE_SIZE = 50;

    private final RestClient http;
    private final long minIntervalMs;
    private long lastRequestAt;

    public AbGrAdapter(@Value("${sm.supermarkets.ab.base-url:https://www.ab.gr}") String baseUrl,
                       @Value("${sm.supermarkets.ab.min-interval-ms:500}") long minIntervalMs) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("User-Agent", "supermarket-manager/0.1 (personal grocery planner)").build();
        this.minIntervalMs = minIntervalMs;
    }

    @Override
    public String id() {
        return "ab";
    }

    @Override
    public String displayName() {
        return "AB Βασιλόπουλος";
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.SEARCH, Capability.CART_HANDOFF);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Price(Double value, Double unitPrice, String supplementaryPriceLabel2, Double wasPrice, Boolean showStrikethroughPrice) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Stock(Boolean inStock) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Promotion(String code, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawProduct(String code, String name, String manufacturerName, String url, Price price, Stock stock,
                      List<Promotion> potentialPromotions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pagination(Integer totalResults, Integer totalPages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Search(List<RawProduct> products, Pagination pagination) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(Search productSearch) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Error(String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Data data, List<Error> errors) {
    }

    @Override
    public List<Product> search(String query, int pages) {
        var out = new ArrayList<Product>();
        String now = Instant.now().toString();
        for (int page = 0; page < Math.max(1, pages); page++) {
            Response r = call(query, page);
            if (r.errors() != null && !r.errors().isEmpty()) throw new IllegalStateException("AB search failed: " + r.errors().getFirst().message());
            if (r.data() == null || r.data().productSearch() == null || r.data().productSearch().products() == null) break;
            Search s = r.data().productSearch();
            for (RawProduct p : s.products()) {
                Product product = toProduct(p, now);
                if (product != null) out.add(product);
            }
            if (s.pagination() == null || s.pagination().totalPages() == null || page + 1 >= s.pagination().totalPages()) break;
        }
        return out;
    }

    private synchronized Response call(String query, int page) {
        long wait = lastRequestAt + minIntervalMs - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
        return http.post().uri("/api/v1/").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .body(Map.of("operationName", "GetProductSearch", "query", QUERY,
                        "variables", Map.of("lang", "gr", "searchQuery", query, "pageNumber", page, "pageSize", PAGE_SIZE)))
                .retrieve()
                .onStatus(st -> st.isError(), (req, resp) -> {
                    throw new IllegalStateException("AB search failed: HTTP " + resp.getStatusCode().value());
                })
                .body(Response.class);
    }

    static Product toProduct(RawProduct p, String now) {
        if (p.price() == null || p.price().value() == null) return null;
        if (p.stock() != null && Boolean.FALSE.equals(p.stock().inStock())) return null;
        var pack = Units.parseLabel(p.price().supplementaryPriceLabel2()).or(() -> Units.parsePackFromName(p.name()));
        String unit = pack.map(s -> s.measure().code().equals("each") ? "pcs" : s.measure().code()).orElse(null);
        Double qty = pack.map(Units.PackSize::amount).orElse(null);
        boolean discount = Boolean.TRUE.equals(p.price().showStrikethroughPrice()) || p.price().wasPrice() != null;
        String brand = p.manufacturerName();
        boolean privateLabel = brand != null && (Text.norm(brand).equals("αβ") || Text.norm(brand).startsWith("αβ "));
        String url = p.url() == null ? null : "https://www.ab.gr" + p.url();
        var offer = new RetailerOffer("ab", "AB Βασιλόπουλος", p.price().value(), null, discount, now, url);
        return new Product("ab", p.code(), p.name(), brand, null, unit, qty, privateLabel, List.of(offer));
    }
}
