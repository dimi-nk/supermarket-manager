package com.supermarketmanager.prices;

/** Fee charged per order, waived from {@code freeAbove}; the store can't be used below {@code minBasket}. */
public record RetailerSetting(String retailer, String displayName, double fee, Double freeAbove, Double minBasket) {
}
