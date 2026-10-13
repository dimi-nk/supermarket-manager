package com.supermarketmanager.prices;

/**
 * @param maxRetailers          default store limit for the optimizer
 * @param packRoundingTolerance allowed undershoot when rounding to whole packs
 * @param maxPriceAgeDays       older prices produce a warning
 * @param searchPages           result pages (50 products each) per PosoKanei search
 */
public record PriceSettings(int maxRetailers, double packRoundingTolerance, int maxPriceAgeDays, int searchPages) {
    public PriceSettings {
        if (maxRetailers < 1 || maxRetailers > 10) throw new IllegalArgumentException("maxRetailers must be 1..10");
        if (packRoundingTolerance < 0 || packRoundingTolerance > 0.5) throw new IllegalArgumentException("packRoundingTolerance must be 0..0.5");
        if (searchPages < 1 || searchPages > 5) throw new IllegalArgumentException("searchPages must be 1..5");
    }
}
