package com.supermarketmanager.core.spi;

/**
 * @param maxRetailers          most stores a plan may split across (optimizer)
 * @param retailer              restrict to one retailer (single-store resolvers)
 * @param packRoundingTolerance allowed undershoot when rounding to whole packs (0.1: needing 1.08 packs buys 1)
 */
public record ResolveOptions(Integer maxRetailers, String retailer, Double packRoundingTolerance) {

    public static final double DEFAULT_TOLERANCE = 0.1;

    public static ResolveOptions defaults() {
        return new ResolveOptions(null, null, null);
    }

    public double tolerance() {
        return packRoundingTolerance == null ? DEFAULT_TOLERANCE : packRoundingTolerance;
    }
}
