package com.supermarketmanager.core.model;

import java.util.List;

/**
 * The final basket: what to buy where, and what could not be bought.
 *
 * @param alternatives cheapest plan for each store-count limit that gives a different answer
 */
public record ResolvedBasket(String resolver, String generatedAt, Double total, List<StoreBasket> stores,
                             List<UnresolvedLine> unresolved, List<Alternative> alternatives, List<String> warnings) {

    public record Alternative(int maxRetailers, double total, List<String> retailers) {
    }

    public ResolvedBasket {
        stores = stores == null ? List.of() : List.copyOf(stores);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
