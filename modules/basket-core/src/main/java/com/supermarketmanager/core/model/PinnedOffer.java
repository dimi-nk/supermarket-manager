package com.supermarketmanager.core.model;

/**
 * A price the user already knows for a line ("2 x Fage 500g at AB, 2.49 each").
 * Lets a hand-made basket skip candidate search entirely.
 *
 * @param packSize pack size in the line's measure; null = the line quantity is one pack
 */
public record PinnedOffer(String retailer, String retailerName, Double price, Double packSize) {
}
