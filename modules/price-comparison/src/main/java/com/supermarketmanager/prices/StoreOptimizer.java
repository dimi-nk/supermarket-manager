package com.supermarketmanager.prices;

import com.supermarketmanager.core.basic.Explain;
import com.supermarketmanager.core.fill.LineFiller;
import com.supermarketmanager.core.fill.StorePlan.Assigned;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;
import com.supermarketmanager.core.model.UnresolvedLine;
import com.supermarketmanager.core.spi.BasketResolver;
import com.supermarketmanager.core.spi.ResolveOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Multi-store resolver: cheapest combination of at most N retailers, with fees and free-delivery thresholds. */
@Component
public class StoreOptimizer implements BasketResolver {
    public static final String ID = "optimizer";
    private final PriceSettingsRepository settings;

    public StoreOptimizer(PriceSettingsRepository settings) {
        this.settings = settings;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Cheapest combination of at most N stores, including delivery fees and free-delivery thresholds";
    }

    @Override
    public ResolvedBasket resolve(CandidateSet set, ResolveOptions options) {
        PriceSettings s = settings.settings();
        int maxRetailers = options.maxRetailers() != null ? options.maxRetailers() : s.maxRetailers();
        double tolerance = options.packRoundingTolerance() != null ? options.packRoundingTolerance() : s.packRoundingTolerance();
        var fees = settings.fees();

        var choices = new ArrayList<Optimizer.Choice>();
        var unresolved = new ArrayList<UnresolvedLine>();
        Map<String, LineCandidates> byLine = new LinkedHashMap<>();
        for (LineCandidates lc : set.lines()) {
            byLine.put(lc.line().id(), lc);
            Map<String, List<Candidate>> byRetailer = lc.candidates().stream()
                    .filter(Candidate::priced)
                    .filter(c -> options.retailer() == null || options.retailer().equals(c.retailer()))
                    .collect(Collectors.groupingBy(Candidate::retailer, TreeMap::new, Collectors.toList()));
            Map<String, Assigned> fills = new TreeMap<>();
            byRetailer.forEach((retailer, cands) -> LineFiller.cheapest(lc.line(), cands, lc.mixPolicy(), tolerance)
                    .ifPresent(rl -> fills.put(retailer, new Assigned(retailer, cands.getFirst().retailerName(), rl))));
            if (fills.isEmpty()) {
                String reason = !lc.candidates().isEmpty() && lc.candidates().stream().noneMatch(Candidate::priced)
                        ? "candidates have no prices - the optimizer needs a price source" : Explain.noCandidates(lc, options.retailer());
                unresolved.add(new UnresolvedLine(lc.line().id(), lc.line().label(), UnresolvedLine.Status.UNFULFILLED, reason));
            } else {
                choices.add(new Optimizer.Choice(lc.line().id(), fills));
            }
        }

        Optimizer.Result result = Optimizer.optimize(choices, maxRetailers, fees);
        var warnings = new ArrayList<>(set.warnings());
        if (result.pruned()) warnings.add("Many retailers matched; the search was limited to the " + Optimizer.MAX_SEARCH_RETAILERS + " with the best coverage.");
        Optimizer.Combination best = result.best();
        List<StoreBasket> stores = best == null ? List.of() : best.stores();
        if (best != null) {
            for (String lineId : best.uncovered()) {
                LineCandidates lc = byLine.get(lineId);
                unresolved.add(new UnresolvedLine(lineId, lc.line().label(), UnresolvedLine.Status.LEFT_OUT,
                        "no store within the " + maxRetailers + "-store limit carries an acceptable product"));
            }
            for (StoreBasket store : stores) {
                if (store.belowMinimum()) warnings.add(store.retailerName() + " is below its minimum basket; this plan cannot be ordered there as-is.");
                for (ResolvedLine line : store.lines()) {
                    for (FillPart part : line.parts()) {
                        Candidate c = part.candidate();
                        long age = ageDays(c.lastUpdated());
                        if (age > s.maxPriceAgeDays()) warnings.add("Price for \"" + c.name() + "\" at " + store.retailerName() + " is " + age + " days old.");
                        if (c.unitPriceMismatch()) warnings.add("Unit price for \"" + c.name() + "\" at " + store.retailerName()
                                + " disagrees with the provider's figure - check the pack size.");
                    }
                }
            }
        }
        var alternatives = result.bySize().entrySet().stream()
                .map(e -> new ResolvedBasket.Alternative(e.getKey(), e.getValue().total(), e.getValue().retailers())).toList();
        return new ResolvedBasket(ID, Instant.now().toString(), best == null ? null : best.total(), stores, unresolved, alternatives, warnings);
    }

    private static long ageDays(String iso) {
        if (iso == null) return 0;
        try {
            return Duration.between(Instant.parse(iso), Instant.now()).toDays();
        } catch (DateTimeParseException e) {
            return 0;
        }
    }
}
