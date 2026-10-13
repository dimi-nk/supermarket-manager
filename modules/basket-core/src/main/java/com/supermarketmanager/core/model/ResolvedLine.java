package com.supermarketmanager.core.model;

import java.util.List;

/** How a basket line is filled at one store. {@code cost} is null if any part is unpriced. */
public record ResolvedLine(String lineId, String label, Measure measure, double need, double amount,
                           Double cost, List<FillPart> parts) {

    public ResolvedLine {
        parts = parts == null ? List.of() : List.copyOf(parts);
    }
}
