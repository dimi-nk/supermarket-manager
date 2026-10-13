package com.supermarketmanager.core;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.Measure;

import java.util.List;

/** Small builders shared by module tests (published as basket-core's test-jar). */
public final class TestData {
    private TestData() {
    }

    public static BasketLine line(String id, Measure m, double qty) {
        return new BasketLine(id, id, null, m, qty, null, List.of(), null, null, null, null);
    }

    public static BasketLine line(String id, Measure m, double qty, String category, List<String> path) {
        return new BasketLine(id, id, null, m, qty, category, path, null, null, null, null);
    }

    public static Candidate cand(String product, String brand, String retailer, double pack, double price) {
        return cand(product, brand, retailer, pack, price, Measure.KG);
    }

    public static Candidate cand(String product, String brand, String retailer, double pack, double price, Measure m) {
        return new Candidate("test", product, product, brand, null, false, m, pack, retailer, retailer, price,
                false, null, null, null);
    }
}
