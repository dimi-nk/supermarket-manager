package com.supermarketmanager.core.model;

import java.util.List;

/** Everything bought at one retailer. */
public record StoreBasket(String retailer, String retailerName, double subtotal, double fee, double total,
                          boolean belowMinimum, List<ResolvedLine> lines) {

    public StoreBasket {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
