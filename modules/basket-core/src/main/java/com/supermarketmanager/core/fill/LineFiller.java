package com.supermarketmanager.core.fill;

import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.MixPolicy;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cheapest way to cover one line's need with whole packs from a set of candidates (normally one retailer's).
 * <ul>
 *   <li>SINGLE_PRODUCT: {@code packs = max(1, ceil(need/pack - tolerance))} of the best single product.</li>
 *   <li>SINGLE_BRAND / ANY: unbounded knapsack (min cost to reach the need) over the allowed products,
 *       per brand or across all of them. Quantities are discretised to 5 g / 5 ml / 1 piece.</li>
 * </ul>
 * Unpriced candidates are only used when nothing priced exists; their fill has a null cost.
 */
public final class LineFiller {
    private static final double EPS = 1e-9;

    private LineFiller() {
    }

    public static Optional<ResolvedLine> cheapest(BasketLine line, List<Candidate> candidates, MixPolicy policy, double tolerance) {
        double need = line.quantity();
        if (need <= EPS || candidates.isEmpty()) return Optional.empty();
        List<Candidate> priced = candidates.stream().filter(Candidate::priced).toList();
        if (priced.isEmpty()) return Optional.of(unpriced(line, candidates.getFirst(), tolerance));
        Optional<List<FillPart>> parts = switch (policy == null ? MixPolicy.SINGLE_PRODUCT : policy) {
            case SINGLE_PRODUCT -> singleProduct(need, priced, tolerance).map(List::of);
            case SINGLE_BRAND -> byBrand(priced).values().stream()
                    .map(group -> knapsack(need, line.measure(), group, tolerance))
                    .flatMap(Optional::stream)
                    .min(PARTS_ORDER);
            case ANY -> knapsack(need, line.measure(), priced, tolerance);
        };
        return parts.map(p -> toLine(line, p));
    }

    static Optional<FillPart> singleProduct(double need, List<Candidate> priced, double tolerance) {
        return priced.stream().map(c -> {
            int packs = Math.max(1, (int) Math.ceil(need / c.packSize() - tolerance - EPS));
            return new FillPart(c, packs, Text.roundMoney(c.price() * packs));
        }).min(Comparator.comparingLong((FillPart f) -> Math.round(f.cost() * 100))
                .thenComparingDouble(f -> f.candidate().unitPrice())
                .thenComparingInt(FillPart::packs)
                .thenComparing(f -> f.candidate().productId()));
    }

    static Optional<List<FillPart>> knapsack(double need, Measure measure, List<Candidate> group, double tolerance) {
        if (group.isEmpty()) return Optional.empty();
        if (group.size() == 1) return singleProduct(need, group, tolerance).map(List::of);
        double step = measure == Measure.EACH ? 1 : 0.005;
        double minPack = group.stream().mapToDouble(Candidate::packSize).min().orElse(0);
        int target = Math.max(1, (int) Math.ceil((need - tolerance * minPack) / step - EPS));
        int n = group.size();
        int[] weight = new int[n];
        long[] cents = new long[n];
        for (int i = 0; i < n; i++) {
            weight[i] = Math.max(1, (int) Math.round(group.get(i).packSize() / step));
            cents[i] = Math.round(group.get(i).price() * 100);
        }
        long[] best = new long[target + 1];
        int[] packs = new int[target + 1];
        int[] choice = new int[target + 1];
        for (int t = 1; t <= target; t++) {
            best[t] = Long.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                int prev = Math.max(0, t - weight[i]);
                if (best[prev] == Long.MAX_VALUE) continue;
                long cost = best[prev] + cents[i];
                int p = packs[prev] + 1;
                if (cost < best[t] || (cost == best[t] && p < packs[t])) {
                    best[t] = cost;
                    packs[t] = p;
                    choice[t] = i;
                }
            }
        }
        Map<Integer, Integer> count = new LinkedHashMap<>();
        for (int t = target; t > 0; t = Math.max(0, t - weight[choice[t]])) count.merge(choice[t], 1, Integer::sum);
        var parts = new ArrayList<FillPart>();
        count.forEach((i, k) -> parts.add(new FillPart(group.get(i), k, Text.roundMoney(group.get(i).price() * k))));
        parts.sort(Comparator.comparing((FillPart f) -> f.candidate().productId()));
        return Optional.of(parts);
    }

    private static final Comparator<List<FillPart>> PARTS_ORDER = Comparator
            .comparingLong((List<FillPart> p) -> Math.round(p.stream().mapToDouble(FillPart::cost).sum() * 100))
            .thenComparingInt(p -> p.stream().mapToInt(FillPart::packs).sum());

    private static Map<String, List<Candidate>> byBrand(List<Candidate> priced) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate c : priced) {
            String key = c.brand() == null || c.brand().isBlank() ? "product:" + c.productId() : Text.norm(c.brand());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
        }
        return groups;
    }

    private static ResolvedLine unpriced(BasketLine line, Candidate c, double tolerance) {
        int packs = Math.max(1, (int) Math.ceil(line.quantity() / c.packSize() - tolerance - EPS));
        return toLine(line, List.of(new FillPart(c, packs, null)));
    }

    public static ResolvedLine toLine(BasketLine line, List<FillPart> parts) {
        double amount = parts.stream().mapToDouble(FillPart::amount).sum();
        boolean allPriced = parts.stream().allMatch(p -> p.cost() != null);
        Double cost = allPriced ? Text.roundMoney(parts.stream().mapToDouble(FillPart::cost).sum()) : null;
        return new ResolvedLine(line.id(), line.label(), line.measure(), line.quantity(),
                Math.round(amount * 1e6) / 1e6, cost, parts);
    }
}
