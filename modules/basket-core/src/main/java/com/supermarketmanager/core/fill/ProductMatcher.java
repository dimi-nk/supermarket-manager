package com.supermarketmanager.core.fill;

import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.ProductRef;
import com.supermarketmanager.core.text.Text;

/** Decides whether a provider product is the concrete product a basket line asks for. */
public final class ProductMatcher {
    private ProductMatcher() {
    }

    public static boolean matches(ProductRef ref, Product product) {
        if (ref == null) return true;
        if (notBlank(ref.barcode()) && notBlank(product.barcode())) return ref.barcode().trim().equals(product.barcode().trim());
        if (notBlank(ref.id()) && notBlank(ref.source()) && ref.source().equals(product.source())) return ref.id().equals(product.id());
        if (notBlank(ref.barcode())) return false; // asked for a barcode the product doesn't carry
        String haystack = (product.brand() == null ? "" : product.brand() + " ") + product.name();
        if (notBlank(ref.brand()) && !Text.containsAllTokens(haystack, ref.brand())) return false;
        // Size tokens ("500g", "2%") are spelled differently by every shop; pack size is checked separately.
        String words = String.join(" ", Text.tokens(ref.name()).stream().filter(t -> !t.matches(".*\\d.*")).toList());
        if (notBlank(ref.brand())) {
            for (String b : Text.tokens(ref.brand())) words = (" " + words + " ").replace(" " + b + " ", " ").trim();
        }
        return words.isBlank() || Text.containsAllTokens(haystack, words);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
