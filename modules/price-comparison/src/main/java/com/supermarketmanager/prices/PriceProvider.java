package com.supermarketmanager.prices;

import com.supermarketmanager.core.model.Product;

import java.util.List;

/** A multi-retailer price source (one product, offers from many chains). */
public interface PriceProvider {

    /** Short id, e.g. "posokanei", "demo". The candidate source id is "price:" + id. */
    String id();

    String description();

    /** Products matching a free-text query, each with its retailer offers. */
    List<Product> search(String query, int pages);
}
