package com.supermarketmanager.prices;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.RetailerOffer;
import com.supermarketmanager.core.text.Text;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Offline provider over a bundled JSON file of SYNTHETIC products and prices (invented, for demos and tests).
 * Never present its prices as real.
 */
@Component
public class DemoProvider implements PriceProvider {
    private final List<Product> products;

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawOffer(String retailer, String retailerName, double price, Double unitPrice, boolean isDiscount, String lastUpdated) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawProduct(String id, String name, String brand, String barcode, String unit, Double unitQuantity,
                      boolean privateLabel, List<RawOffer> offers) {
    }

    public DemoProvider(ObjectMapper json) {
        try (InputStream in = new ClassPathResource("fixtures/demo-products.json").getInputStream()) {
            List<RawProduct> raw = json.readValue(in, new TypeReference<>() {
            });
            products = raw.stream().map(r -> new Product("demo", r.id(), r.name(), r.brand(), r.barcode(), r.unit(),
                    r.unitQuantity(), r.privateLabel(), r.offers().stream().map(o -> new RetailerOffer(o.retailer(),
                    o.retailerName(), o.price(), o.unitPrice(), o.isDiscount(), o.lastUpdated(), null)).toList())).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String id() {
        return "demo";
    }

    @Override
    public String description() {
        return "Synthetic demo prices for 5 made-up chains (offline; NOT real prices)";
    }

    @Override
    public List<Product> search(String query, int pages) {
        return products.stream().filter(p -> Text.containsAllTokens((p.brand() == null ? "" : p.brand() + " ") + p.name(), query)).toList();
    }
}
