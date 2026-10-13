package com.supermarketmanager.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One purchasable option for a basket line: a product, its pack size in the line's measure, and a retailer price.
 * {@code price} may be null for sources that know products but not prices (e.g. the local catalog).
 */
public record Candidate(String source, String productId, String name, String brand, String barcode,
                        boolean privateLabel, Measure measure, double packSize,
                        String retailer, String retailerName, Double price, boolean discount,
                        String lastUpdated, Double reportedUnitPrice, String url) {

    public boolean priced() {
        return price != null && Double.isFinite(price) && price > 0;
    }

    /** EUR per kg / l / piece, computed from price and pack size (never taken from the provider). */
    @JsonProperty("unitPrice")
    public Double unitPrice() {
        return priced() && packSize > 0 ? price / packSize : null;
    }

    /** Provider's unit price disagrees with ours by more than 30%: the pack size may be mis-parsed. */
    @JsonProperty("unitPriceMismatch")
    public boolean unitPriceMismatch() {
        Double ours = unitPrice();
        return ours != null && reportedUnitPrice != null && reportedUnitPrice > 0
                && Math.abs(reportedUnitPrice - ours) / ours > 0.3;
    }
}
