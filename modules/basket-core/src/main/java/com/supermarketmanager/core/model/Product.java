package com.supermarketmanager.core.model;

import java.util.List;

/**
 * A product as returned by a price source (PosoKanei, a supermarket API, the local catalog).
 * {@code unit}/{@code unitQuantity} are the provider's raw values; pack size is resolved from them or from the name.
 */
public record Product(String source, String id, String name, String brand, String barcode,
                      String unit, Double unitQuantity, boolean privateLabel, List<RetailerOffer> offers) {

    public Product {
        offers = offers == null ? List.of() : List.copyOf(offers);
    }
}
