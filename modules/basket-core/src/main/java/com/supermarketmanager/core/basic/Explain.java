package com.supermarketmanager.core.basic;

import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;

import java.util.ArrayList;
import java.util.Map;

/** Human-readable reasons for lines that could not be filled. */
public final class Explain {
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("notRequestedProduct", "not the requested product"),
            Map.entry("queryMismatch", "not matching every query word"),
            Map.entry("unknownPack", "with unreadable pack size"),
            Map.entry("wrongMeasure", "sold in a different unit"),
            Map.entry("otherPackSize", "in a different pack size"),
            Map.entry("invalidPrice", "offers without a valid price"),
            Map.entry("denied", "denied"),
            Map.entry("notAllowed", "not on the allow list"),
            Map.entry("privateLabel", "private-label"),
            Map.entry("unitPrice", "offers outside the unit-price range"),
            Map.entry("packPrice", "offers outside the pack-price range"),
            Map.entry("packSize", "outside pack-size limits"),
            Map.entry("retailer", "offers at excluded retailers"),
            Map.entry("onlyDiscounted", "offers not on discount"));

    private Explain() {
    }

    public static String noCandidates(LineCandidates lc, String retailer) {
        Diagnostics d = lc.diagnostics();
        if (!lc.candidates().isEmpty() && retailer != null) return "no acceptable candidate at " + retailer;
        if (d.searched() == 0 && d.rejected().isEmpty()) {
            return d.notes().isEmpty() ? "no products found - try a different query" : String.join("; ", d.notes());
        }
        var parts = new ArrayList<String>();
        parts.add(d.searched() + " products found");
        d.rejected().forEach((k, v) -> parts.add(v + " " + LABELS.getOrDefault(k, k)));
        String text = String.join("; ", parts);
        if (d.closestRejected() != null) {
            text += ". Closest: " + d.closestRejected().name() + " at " + d.closestRejected().retailerName()
                    + ", €" + d.closestRejected().unitPrice() + "/" + lc.line().measure().code();
        }
        return text;
    }
}
