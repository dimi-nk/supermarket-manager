package com.supermarketmanager.core.basic;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.PinnedOffer;
import com.supermarketmanager.core.model.ProductRef;
import com.supermarketmanager.core.spi.CandidateSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidate source for hand-made baskets: each line's pinned offer becomes its only candidate.
 * Lines without an offer get no candidates. Lets you run item rules or a resolver without any price lookup.
 */
public class PinnedOfferSource implements CandidateSource {
    public static final String ID = "basket";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Use the prices written on the basket lines (no lookups)";
    }

    @Override
    public CandidateSet findCandidates(Basket basket) {
        var lines = new ArrayList<LineCandidates>();
        var warnings = new ArrayList<String>();
        for (BasketLine line : basket.openLines()) {
            PinnedOffer offer = line.offer();
            if (offer == null) {
                lines.add(new LineCandidates(line, List.of(),
                        new Diagnostics.Builder().note("line has no pinned offer").build(), null));
                continue;
            }
            ProductRef p = line.product();
            double pack = offer.packSize() != null ? offer.packSize()
                    : p != null && p.packSize() != null ? p.packSize() : line.quantity();
            String retailer = offer.retailer() == null ? "unspecified" : offer.retailer();
            var c = new Candidate("basket", p != null && p.id() != null ? p.id() : line.id(),
                    p != null && p.name() != null ? p.name() : line.label(), p == null ? null : p.brand(),
                    p == null ? null : p.barcode(), p != null && Boolean.TRUE.equals(p.privateLabel()),
                    line.measure(), pack, retailer, offer.retailerName() == null ? retailer : offer.retailerName(),
                    offer.price(), false, null, null, null);
            if (!c.priced()) warnings.add("Line '" + line.id() + "' has no price; costs will be incomplete.");
            lines.add(new LineCandidates(line, List.of(c), new Diagnostics.Builder().searched(1).build(), null));
        }
        return new CandidateSet(ID, lines, warnings);
    }
}
