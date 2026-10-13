package com.supermarketmanager.prices;

import com.supermarketmanager.core.fill.StorePlan;
import com.supermarketmanager.core.fill.StorePlan.Assigned;
import com.supermarketmanager.core.fill.StorePlan.Fees;
import com.supermarketmanager.core.model.StoreBasket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Chooses the cheapest set of at most {@code maxRetailers} stores (ported from the TypeScript planner).
 * <ol>
 *   <li>Per line, the cheapest acceptable fill at each retailer is given ({@link Choice}).</li>
 *   <li>Every retailer subset up to the limit is tried (pruned to the 14 best-covering retailers).</li>
 *   <li>In a subset each line goes to its cheapest store; fees apply per used store and are waived at {@code freeAbove}.</li>
 *   <li>A greedy pass moves lines into stores just below their free-delivery threshold when that lowers the total.</li>
 *   <li>Ranking: fewer minimum-basket violations, fewer uncovered lines, lower total (to the cent), fewer stores, ids.</li>
 * </ol>
 * Exact without fees or with flat fees; with free-delivery thresholds the greedy step is a heuristic
 * (see OptimizerBruteForceTest for the measured bound).
 */
public final class Optimizer {
    static final int MAX_SEARCH_RETAILERS = 14;

    private Optimizer() {
    }

    public record Choice(String lineId, Map<String, Assigned> byRetailer) {
        double cost(String retailer) {
            Assigned a = byRetailer.get(retailer);
            return a == null || a.line().cost() == null ? Double.MAX_VALUE : a.line().cost();
        }
    }

    public record Combination(Map<String, Assigned> assignment, List<StoreBasket> stores, List<String> covered,
                              List<String> uncovered, double total, int violations) {
        public List<String> retailers() {
            return stores.stream().map(StoreBasket::retailer).toList();
        }
    }

    public record Result(Combination best, Map<Integer, Combination> bySize, boolean pruned) {
    }

    public static Result optimize(List<Choice> choices, int maxRetailers, Map<String, Fees> fees) {
        List<Choice> plannable = choices.stream().filter(c -> !c.byRetailer().isEmpty()).toList();
        if (plannable.isEmpty()) return new Result(null, Map.of(), false);

        List<String> retailers = new ArrayList<>(new TreeSet<>(plannable.stream().flatMap(c -> c.byRetailer().keySet().stream()).toList()));
        boolean pruned = false;
        if (retailers.size() > MAX_SEARCH_RETAILERS) {
            pruned = true;
            Map<String, int[]> coverage = new HashMap<>();
            Map<String, Double> cost = new HashMap<>();
            for (String r : retailers) {
                for (Choice c : plannable) {
                    if (c.byRetailer().containsKey(r)) {
                        coverage.computeIfAbsent(r, k -> new int[1])[0]++;
                        cost.merge(r, c.cost(r), Double::sum);
                    }
                }
            }
            retailers = retailers.stream().sorted(Comparator.comparingInt((String r) -> -coverage.getOrDefault(r, new int[1])[0])
                    .thenComparingDouble(r -> cost.getOrDefault(r, 0.0)).thenComparing(r -> r))
                    .limit(MAX_SEARCH_RETAILERS).toList();
        }

        int maxSize = Math.min(maxRetailers, retailers.size());
        Combination[] bestAtMost = new Combination[maxSize + 1];
        for (int size = 1; size <= maxSize; size++) {
            for (List<String> subset : combinations(retailers, size)) {
                Combination combo = evaluate(plannable, subset, fees, choices);
                for (int k = size; k <= maxSize; k++) {
                    if (bestAtMost[k] == null || compare(combo, bestAtMost[k]) < 0) bestAtMost[k] = combo;
                }
            }
        }
        Map<Integer, Combination> bySize = new LinkedHashMap<>();
        Combination prev = null;
        for (int k = 1; k <= maxSize; k++) {
            Combination c = bestAtMost[k];
            if (c == null) continue;
            if (prev != null && prev.retailers().equals(c.retailers()) && prev.total() == c.total()) continue;
            bySize.put(k, c);
            prev = c;
        }
        return new Result(bestAtMost[maxSize], bySize, pruned);
    }

    private static Combination evaluate(List<Choice> plannable, List<String> subset, Map<String, Fees> fees, List<Choice> all) {
        Map<String, Assigned> assignment = new LinkedHashMap<>();
        for (Choice choice : plannable) {
            Assigned pick = null;
            for (String r : subset) {
                Assigned o = choice.byRetailer().get(r);
                if (o == null) continue;
                if (pick == null || o.line().cost() < pick.line().cost() - 0.004
                        || (Math.abs(o.line().cost() - pick.line().cost()) <= 0.004 && unitPrice(o) < unitPrice(pick))) {
                    pick = o;
                }
            }
            if (pick != null) assignment.put(choice.lineId(), pick);
        }
        Combination combo = build(assignment, fees, all);

        // Free-delivery thresholds: paying a little more for some lines can lift a store over its threshold.
        List<String> thresholdStores = subset.stream().filter(r -> fees.containsKey(r) && fees.get(r).freeAbove() != null).toList();
        for (int pass = 0; pass < 3 && !thresholdStores.isEmpty(); pass++) {
            boolean improved = false;
            for (String r : thresholdStores) {
                double target = fees.get(r).freeAbove();
                double current = combo.stores().stream().filter(s -> s.retailer().equals(r)).mapToDouble(StoreBasket::subtotal).findFirst().orElse(0);
                if (current >= target) continue;
                record Move(String lineId, Assigned to, double extra) {
                }
                var moves = new ArrayList<Move>();
                for (Choice choice : plannable) {
                    Assigned from = assignment.get(choice.lineId());
                    Assigned to = choice.byRetailer().get(r);
                    if (from == null || to == null || from.retailer().equals(r)) continue;
                    moves.add(new Move(choice.lineId(), to, to.line().cost() - from.line().cost()));
                }
                moves.sort(Comparator.comparingDouble(m -> m.extra() / Math.max(m.to().line().cost(), 0.01)));
                var trial = new LinkedHashMap<>(assignment);
                double subtotal = current;
                for (Move m : moves) {
                    if (subtotal >= target) break;
                    trial.put(m.lineId(), m.to());
                    subtotal += m.to().line().cost();
                }
                if (subtotal < target) continue;
                Combination candidate = build(trial, fees, all);
                if (compare(candidate, combo) < 0) {
                    assignment.putAll(trial);
                    combo = candidate;
                    improved = true;
                }
            }
            if (!improved) break;
        }
        return combo;
    }

    private static Combination build(Map<String, Assigned> assignment, Map<String, Fees> fees, List<Choice> all) {
        var assigned = new ArrayList<Assigned>();
        var covered = new ArrayList<String>();
        var uncovered = new ArrayList<String>();
        for (Choice c : all) {
            Assigned a = assignment.get(c.lineId());
            if (a == null) uncovered.add(c.lineId());
            else {
                covered.add(c.lineId());
                assigned.add(a);
            }
        }
        List<StoreBasket> stores = StorePlan.build(assigned, fees);
        int violations = (int) stores.stream().filter(StoreBasket::belowMinimum).count();
        return new Combination(Map.copyOf(assignment), stores, covered, uncovered, StorePlan.total(stores), violations);
    }

    /** Negative if {@code a} is the better plan. */
    static int compare(Combination a, Combination b) {
        int c = Integer.compare(a.violations(), b.violations());
        if (c == 0) c = Integer.compare(a.uncovered().size(), b.uncovered().size());
        if (c == 0) c = Long.compare(Math.round(a.total() * 100), Math.round(b.total() * 100));
        if (c == 0) c = Integer.compare(a.stores().size(), b.stores().size());
        if (c == 0) c = String.join(",", a.retailers()).compareTo(String.join(",", b.retailers()));
        return c;
    }

    private static double unitPrice(Assigned a) {
        return a.line().amount() > 0 && a.line().cost() != null ? a.line().cost() / a.line().amount() : Double.MAX_VALUE;
    }

    static List<List<String>> combinations(List<String> items, int size) {
        var out = new ArrayList<List<String>>();
        combine(items, size, 0, new ArrayList<>(), out);
        return out;
    }

    private static void combine(List<String> items, int size, int start, List<String> prefix, List<List<String>> out) {
        if (prefix.size() == size) {
            out.add(List.copyOf(prefix));
            return;
        }
        for (int i = start; i <= items.size() - (size - prefix.size()); i++) {
            prefix.add(items.get(i));
            combine(items, size, i + 1, prefix, out);
            prefix.removeLast();
        }
    }

    static Set<String> retailersOf(List<Choice> choices) {
        var s = new HashSet<String>();
        choices.forEach(c -> s.addAll(c.byRetailer().keySet()));
        return s;
    }
}
