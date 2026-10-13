package com.supermarketmanager.core.fill;

import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;
import com.supermarketmanager.core.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Groups resolved lines by retailer and applies per-retailer fees. */
public final class StorePlan {

    /** Delivery/service fee, waived at {@code freeAbove}; a store under {@code minBasket} cannot be ordered from. */
    public record Fees(double fee, Double freeAbove, Double minBasket) {
        public static final Fees NONE = new Fees(0, null, null);
    }

    public record Assigned(String retailer, String retailerName, ResolvedLine line) {
    }

    private StorePlan() {
    }

    public static List<StoreBasket> build(List<Assigned> assigned, Map<String, Fees> fees) {
        Map<String, List<Assigned>> byRetailer = new TreeMap<>();
        for (Assigned a : assigned) byRetailer.computeIfAbsent(a.retailer(), k -> new ArrayList<>()).add(a);
        var stores = new ArrayList<StoreBasket>();
        byRetailer.forEach((retailer, lines) -> {
            double subtotal = Text.roundMoney(lines.stream().mapToDouble(a -> a.line().cost() == null ? 0 : a.line().cost()).sum());
            Fees f = fees == null ? Fees.NONE : fees.getOrDefault(retailer, Fees.NONE);
            double fee = f.freeAbove() != null && subtotal >= f.freeAbove() ? 0 : f.fee();
            boolean below = f.minBasket() != null && subtotal < f.minBasket();
            stores.add(new StoreBasket(retailer, lines.getFirst().retailerName(), subtotal, Text.roundMoney(fee),
                    Text.roundMoney(subtotal + fee), below, lines.stream().map(Assigned::line).toList()));
        });
        return stores;
    }

    public static double total(List<StoreBasket> stores) {
        return Text.roundMoney(stores.stream().mapToDouble(StoreBasket::total).sum());
    }
}
