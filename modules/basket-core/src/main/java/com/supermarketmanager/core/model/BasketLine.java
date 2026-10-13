package com.supermarketmanager.core.model;

import java.util.List;

/**
 * One thing to buy. Either generic ("1 kg of strained yoghurt", {@code product == null})
 * or concrete ("Fage Total 2% 500g", {@code product != null}).
 *
 * @param id               stable key, unique within the basket (pantry uses "category:yoghurt" / "product:fage-500")
 * @param query            search text for candidate sources; defaults to product name or label
 * @param quantity         amount needed, in {@code measure}
 * @param categoryId       most specific category, used to scope rules
 * @param categoryPath     category and all its ancestors (most specific first), used to scope rules
 * @param preferredPackSize informational hint (e.g. target was "2 x 500 g tubs")
 * @param offer            optional known price; makes the line usable without any candidate source
 */
public record BasketLine(String id, String label, String query, Measure measure, double quantity,
                         String categoryId, List<String> categoryPath, ProductRef product,
                         Double preferredPackSize, PinnedOffer offer, String note) {

    public BasketLine {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Basket line id is required");
        if (measure == null) throw new IllegalArgumentException("Basket line '" + id + "' needs a measure (kg, l or each)");
        if (quantity < 0 || Double.isNaN(quantity)) throw new IllegalArgumentException("Basket line '" + id + "' has a negative quantity");
        categoryPath = categoryPath == null ? List.of() : List.copyOf(categoryPath);
        if (label == null || label.isBlank()) label = product != null && product.name() != null ? product.name() : id;
    }

    /** Text to search providers with. */
    public String searchText() {
        if (query != null && !query.isBlank()) return query;
        if (product != null && product.name() != null) return product.name();
        return label;
    }

    public boolean inCategory(String category) {
        return category != null && (category.equals(categoryId) || categoryPath.contains(category));
    }

    public BasketLine withQuantity(double newQuantity) {
        return new BasketLine(id, label, query, measure, newQuantity, categoryId, categoryPath, product,
                preferredPackSize, offer, note);
    }
}
