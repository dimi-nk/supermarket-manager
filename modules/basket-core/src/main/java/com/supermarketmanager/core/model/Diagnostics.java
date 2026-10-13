package com.supermarketmanager.core.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Why candidates were dropped for one line. Stages append to it as the set moves through the pipeline.
 *
 * @param searched        products the candidate source looked at
 * @param rejected        reason -> count (e.g. "denied", "wrongMeasure", "overUnitPrice")
 * @param closestRejected cheapest candidate that only failed a price limit, to help tune caps
 */
public record Diagnostics(int searched, Map<String, Integer> rejected, ClosestRejected closestRejected, List<String> notes) {

    public record ClosestRejected(String name, String retailerName, double unitPrice, String reason) {
    }

    public Diagnostics {
        rejected = rejected == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(rejected));
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public static Diagnostics empty() {
        return new Diagnostics(0, Map.of(), null, List.of());
    }

    public int rejectedTotal() {
        return rejected.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Mutable accumulator. */
    public static final class Builder {
        private int searched;
        private final Map<String, Integer> rejected = new LinkedHashMap<>();
        private ClosestRejected closest;
        private final java.util.ArrayList<String> notes = new java.util.ArrayList<>();

        public Builder() {
        }

        private Builder(Diagnostics base) {
            searched = base.searched;
            rejected.putAll(base.rejected);
            closest = base.closestRejected;
            notes.addAll(base.notes);
        }

        public Builder searched(int n) {
            searched = n;
            return this;
        }

        public Builder reject(String reason) {
            rejected.merge(reason, 1, Integer::sum);
            return this;
        }

        public Builder note(String note) {
            notes.add(note);
            return this;
        }

        public Builder overCap(Candidate c, String reason) {
            Double unit = c.unitPrice();
            if (unit != null && (closest == null || unit < closest.unitPrice())) {
                closest = new ClosestRejected(c.name(), c.retailerName(), Math.round(unit * 100) / 100.0, reason);
            }
            return this;
        }

        public Diagnostics build() {
            return new Diagnostics(searched, rejected, closest, notes);
        }
    }
}
