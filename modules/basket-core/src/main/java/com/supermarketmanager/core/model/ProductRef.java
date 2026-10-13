package com.supermarketmanager.core.model;

/**
 * Identifies a concrete product a basket line asks for ("Fage Total 2% 500g").
 * Matching against provider products uses, in order: barcode, (source,id), then name/brand tokens.
 *
 * @param id       catalog or provider product id
 * @param source   who issued {@code id} (e.g. "catalog", "posokanei", "ab"); null = unknown
 * @param packSize pack size in the line's measure, if known
 */
public record ProductRef(String id, String source, String name, String brand, String barcode,
                         Double packSize, Boolean privateLabel) {
}
