package com.supermarketmanager.core.basic;

import com.supermarketmanager.core.fill.LineFiller;
import com.supermarketmanager.core.fill.StorePlan;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.UnresolvedLine;
import com.supermarketmanager.core.spi.BasketResolver;
import com.supermarketmanager.core.spi.ResolveOptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simplest resolver: every line goes to the retailer where it is cheapest (no store limit, no fees),
 * or to {@code options.retailer} when given. Needs nothing but core, so baskets can be finalised
 * without the price-comparison module.
 */
public class CheapestPerLineResolver implements BasketResolver {
    public static final String ID = "cheapest-per-line";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Each line at its cheapest retailer (or only the given retailer); ignores store count and fees";
    }

    @Override
    public ResolvedBasket resolve(CandidateSet set, ResolveOptions options) {
        var assigned = new ArrayList<StorePlan.Assigned>();
        var unresolved = new ArrayList<UnresolvedLine>();
        for (LineCandidates lc : set.lines()) {
            List<Candidate> pool = lc.candidates();
            if (options.retailer() != null) pool = pool.stream().filter(c -> options.retailer().equals(c.retailer())).toList();
            Map<String, List<Candidate>> byRetailer = pool.stream()
                    .collect(Collectors.groupingBy(Candidate::retailer, java.util.TreeMap::new, Collectors.toList()));
            Optional<StorePlan.Assigned> best = byRetailer.values().stream()
                    .map(cs -> LineFiller.cheapest(lc.line(), cs, lc.mixPolicy(), options.tolerance())
                            .map(rl -> new StorePlan.Assigned(cs.getFirst().retailer(), cs.getFirst().retailerName(), rl)))
                    .flatMap(Optional::stream)
                    .min(Comparator.comparingDouble((StorePlan.Assigned a) -> costOrMax(a.line())));
            if (best.isPresent()) assigned.add(best.get());
            else unresolved.add(new UnresolvedLine(lc.line().id(), lc.line().label(), UnresolvedLine.Status.UNFULFILLED,
                    Explain.noCandidates(lc, options.retailer())));
        }
        var stores = StorePlan.build(assigned, Map.of());
        var warnings = new ArrayList<>(set.warnings());
        boolean incomplete = assigned.stream().anyMatch(a -> a.line().cost() == null);
        if (incomplete) warnings.add("Some lines have no price; totals are incomplete.");
        return new ResolvedBasket(ID, Instant.now().toString(), incomplete ? null : StorePlan.total(stores), stores,
                unresolved, List.of(), warnings);
    }

    private static double costOrMax(ResolvedLine l) {
        return l.cost() == null ? Double.MAX_VALUE : l.cost();
    }
}
