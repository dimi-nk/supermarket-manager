package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.MixPolicy;
import com.supermarketmanager.core.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Filters a candidate set through a rule set. Pure: no I/O. */
public final class RuleEngine {
    private static final double EPS = 1e-9;

    private RuleEngine() {
    }

    public static CandidateSet apply(CandidateSet set, RuleSet rules) {
        var lines = new ArrayList<LineCandidates>();
        var warnings = new ArrayList<String>();
        for (LineCandidates lc : set.lines()) {
            LineCandidates filtered = applyToLine(lc, rules.rules());
            if (!lc.candidates().isEmpty() && filtered.candidates().isEmpty()) {
                warnings.add("Rules removed every candidate for '" + lc.line().label() + "'.");
            }
            lines.add(filtered);
        }
        return set.withLines(lines, warnings);
    }

    public static LineCandidates applyToLine(LineCandidates lc, List<Rule> all) {
        BasketLine line = lc.line();
        List<Rule> rules = all.stream().filter(r -> r.appliesTo(line)).toList();
        var diag = lc.diagnostics().toBuilder();
        var kept = new ArrayList<Candidate>();
        for (Candidate c : lc.candidates()) {
            String reason = rejection(c, rules, diag);
            if (reason == null) kept.add(c);
            else diag.reject(reason);
        }
        MixPolicy mix = rules.stream().filter(r -> r.type() == RuleType.MIX_POLICY)
                .max(Comparator.comparingInt(r -> r.specificity(line)))
                .map(Rule::mix).orElse(lc.mixPolicy());
        return lc.with(kept, diag.build(), mix);
    }

    /** Null if acceptable, otherwise the diagnostic reason key. */
    static String rejection(Candidate c, List<Rule> rules, Diagnostics.Builder diag) {
        for (Rule r : rules) if (r.type() == RuleType.DENY && r.values().stream().anyMatch(v -> matchesProduct(c, v))) return "denied";
        for (Rule r : rules) if (r.type() == RuleType.NO_PRIVATE_LABEL && c.privateLabel()) return "privateLabel";
        for (Rule r : rules) if (r.type() == RuleType.ALLOW && r.values().stream().noneMatch(v -> matchesProduct(c, v))) return "notAllowed";
        for (Rule r : rules) if (r.type() == RuleType.RETAILER_DENY && r.values().stream().anyMatch(v -> matchesRetailer(c, v))) return "retailer";
        for (Rule r : rules) if (r.type() == RuleType.RETAILER_ALLOW && r.values().stream().noneMatch(v -> matchesRetailer(c, v))) return "retailer";
        for (Rule r : rules) if (r.type() == RuleType.PACK_SIZE && !inRange(c.packSize(), r)) return "packSize";
        for (Rule r : rules) if (r.type() == RuleType.ONLY_DISCOUNTED && !c.discount()) return "onlyDiscounted";
        // Price ranges can only be judged on priced candidates; unpriced ones pass and are judged later.
        if (c.priced()) {
            for (Rule r : rules) {
                if (r.type() == RuleType.UNIT_PRICE && !inRange(c.unitPrice(), r)) {
                    diag.overCap(c, "unitPrice");
                    return "unitPrice";
                }
                if (r.type() == RuleType.PACK_PRICE && !inRange(c.price(), r)) {
                    diag.overCap(c, "packPrice");
                    return "packPrice";
                }
            }
        }
        return null;
    }

    static boolean inRange(double v, Rule r) {
        return (r.min() == null || v >= r.min() - EPS) && (r.max() == null || v <= r.max() + EPS);
    }

    /** Brand or name words, or exact "gtin:" / "id:" matchers. */
    public static boolean matchesProduct(Candidate c, String entry) {
        String e = entry == null ? "" : entry.trim();
        if (e.isEmpty()) return false;
        String lower = e.toLowerCase();
        if (lower.startsWith("gtin:")) return c.barcode() != null && c.barcode().equals(e.substring(5).trim());
        if (lower.startsWith("id:")) return e.substring(3).trim().equals(c.productId());
        return (c.brand() != null && Text.containsAllTokens(c.brand(), e)) || Text.containsAllTokens(c.name(), e);
    }

    static boolean matchesRetailer(Candidate c, String entry) {
        String e = Text.norm(entry);
        return !e.isEmpty() && (e.equals(Text.norm(c.retailer())) || e.equals(Text.norm(c.retailerName())));
    }
}
