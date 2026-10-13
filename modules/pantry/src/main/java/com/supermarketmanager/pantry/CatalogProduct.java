package com.supermarketmanager.pantry;

/**
 * A concrete product you buy ("Fage Total 2% 500g"). Its measure is its category's.
 *
 * @param packSize       pack size in the category's measure (0.5 for a 500 g tub)
 * @param externalSource source of {@code externalId} ("posokanei", "ab"), so the product can be found exactly
 */
public record CatalogProduct(String id, String name, String brand, String categoryId, double packSize, String barcode,
                             String externalSource, String externalId, String searchQuery, boolean privateLabel,
                             String notes) {
}
