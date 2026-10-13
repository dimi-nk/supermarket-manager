package com.supermarketmanager.core.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * How one basket line may be filled with packs.
 * SINGLE_PRODUCT: N packs of one product (the default).
 * SINGLE_BRAND: packs of several sizes/variants, but all of one brand.
 * ANY: any mix of acceptable products (cheapest combination wins).
 */
public enum MixPolicy {
    SINGLE_PRODUCT, SINGLE_BRAND, ANY;

    @JsonCreator
    public static MixPolicy of(String value) {
        return value == null ? null : MixPolicy.valueOf(value.trim().toUpperCase().replace('-', '_'));
    }
}
