package com.supermarketmanager.core.model;

/**
 * A retailer's shelf price for one product.
 *
 * @param reportedUnitPrice the provider's own price per kg/l/piece, only used to flag pack-size mistakes
 */
public record RetailerOffer(String retailer, String retailerName, double price, Double reportedUnitPrice,
                            boolean discount, String lastUpdated, String url) {
}
