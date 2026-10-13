package com.supermarketmanager.prices;

import com.supermarketmanager.core.fill.StorePlan.Assigned;
import com.supermarketmanager.core.fill.StorePlan.Fees;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.ResolvedLine;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.supermarketmanager.core.TestData.cand;
import static org.assertj.core.api.Assertions.assertThat;

/** Hand-computed scenarios ported from the TypeScript planner's optimize.test.ts. */
class OptimizerTest {

    static Optimizer.Choice choice(String line, Map<String, Double> prices) {
        var by = new LinkedHashMap<String, Assigned>();
        prices.forEach((r, p) -> {
            var c = cand(line + "-" + r, null, r, 1, p, Measure.EACH);
            by.put(r, new Assigned(r, r.toUpperCase(), new ResolvedLine(line, line, Measure.EACH, 1, 1, p, List.of(new FillPart(c, 1, p)))));
        });
        return new Optimizer.Choice(line, by);
    }

    // x is cheap for A and B, y is cheap for C, z is cheap for nothing.
    final List<Optimizer.Choice> choices = List.of(
            choice("A", Map.of("x", 1.0, "y", 3.0, "z", 4.0)),
            choice("B", Map.of("x", 2.0, "y", 4.0, "z", 5.0)),
            choice("C", Map.of("x", 9.0, "y", 3.0, "z", 6.0)));
    final List<Optimizer.Choice> xy = List.of(
            choice("A", Map.of("x", 1.0, "y", 3.0)),
            choice("B", Map.of("x", 2.0, "y", 4.0)),
            choice("C", Map.of("x", 9.0, "y", 3.0)));

    @Test
    void splitsAcrossTwoStoresWhenCheapest() {
        var r = Optimizer.optimize(choices, 2, Map.of());
        assertThat(r.best().retailers()).containsExactly("x", "y");
        assertThat(r.best().total()).isEqualTo(6.0);
        assertThat(r.bySize()).containsOnlyKeys(1, 2);
        assertThat(r.bySize().get(1).total()).isEqualTo(10.0);
    }

    @Test
    void respectsOneStoreLimit() {
        var r = Optimizer.optimize(choices, 1, Map.of());
        assertThat(r.best().retailers()).containsExactly("y");
        assertThat(r.best().total()).isEqualTo(10.0);
    }

    @Test
    void deliveryFeeCanMakeSingleStoreWin() {
        assertThat(Optimizer.optimize(xy, 2, Map.of("y", new Fees(5, null, null))).best().total()).isEqualTo(11.0);
        var big = Optimizer.optimize(xy, 2, Map.of("y", new Fees(7, null, null))).best();
        assertThat(big.retailers()).containsExactly("x");
        assertThat(big.total()).isEqualTo(12.0);
    }

    @Test
    void freeAboveWaivesFee() {
        var fees = Map.of("x", new Fees(4, 20.0, null));
        var at = Optimizer.optimize(List.of(choice("A", Map.of("x", 20.0))), 1, fees).best();
        assertThat(at.total()).isEqualTo(20.0);
        assertThat(at.stores().getFirst().fee()).isZero();
        assertThat(Optimizer.optimize(List.of(choice("A", Map.of("x", 19.99))), 1, fees).best().total()).isEqualTo(23.99);
    }

    @Test
    void storeBelowMinimumIsAvoidedWhenPossible() {
        var best = Optimizer.optimize(xy, 2, Map.of("y", new Fees(0, null, 11.0))).best();
        assertThat(best.retailers()).containsExactly("x");
        assertThat(best.total()).isEqualTo(12.0);
        assertThat(best.violations()).isZero();
        assertThat(Optimizer.optimize(List.of(choice("A", Map.of("x", 3.0))), 1, Map.of("x", new Fees(0, null, 10.0))).best().violations()).isEqualTo(1);
    }

    @Test
    void coverageBeatsPriceAndTiesPreferFewerStores() {
        var best = Optimizer.optimize(List.of(choice("A", Map.of("x", 1.0, "y", 5.0)), choice("B", Map.of("y", 5.0))), 1, Map.of()).best();
        assertThat(best.retailers()).containsExactly("y");
        assertThat(best.uncovered()).isEmpty();
        var tie = Optimizer.optimize(List.of(choice("A", Map.of("x", 1.0, "y", 1.0)), choice("B", Map.of("x", 1.0, "y", 1.0))), 2, Map.of()).best();
        assertThat(tie.retailers()).hasSize(1);
    }

    @Test
    void feeFreeThirdStoreCanBeatFeeAtSecond() {
        var best = Optimizer.optimize(choices, 2, Map.of("y", new Fees(5, null, null))).best();
        assertThat(best.retailers()).containsExactly("x", "z");
        assertThat(best.total()).isEqualTo(9.0);
    }

    @Test
    void emptyAndUnsuppliableLines() {
        assertThat(Optimizer.optimize(List.of(), 2, Map.of()).best()).isNull();
        var best = Optimizer.optimize(List.of(choice("A", Map.of("x", 2.0)), new Optimizer.Choice("Q", Map.of())), 2, Map.of()).best();
        assertThat(best.covered()).containsExactly("A");
    }
}
