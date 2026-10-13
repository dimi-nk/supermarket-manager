package com.supermarketmanager.core.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** A list of things to buy. Produced by the pantry module or written by hand. */
public record Basket(String name, List<BasketLine> lines, Map<String, String> meta) {

    public Basket {
        lines = lines == null ? List.of() : List.copyOf(lines);
        meta = meta == null ? Map.of() : Map.copyOf(meta);
        var seen = new HashSet<String>();
        for (BasketLine line : lines) {
            if (!seen.add(line.id())) throw new IllegalArgumentException("Duplicate basket line id '" + line.id() + "'");
        }
    }

    public static Basket of(String name, List<BasketLine> lines) {
        return new Basket(name, lines, Map.of());
    }

    /** Lines that still need something bought. */
    public List<BasketLine> openLines() {
        return lines.stream().filter(l -> l.quantity() > 1e-9).toList();
    }
}
