package com.supermarketmanager.prices;

import com.supermarketmanager.core.fill.StorePlan.Fees;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compares the optimizer with exhaustive search over every line -> store assignment.
 * Exact without fees or with flat fees; with free-delivery thresholds it must stay close and never claim
 * a total below the true optimum.
 */
class OptimizerBruteForceTest {
    static final List<String> RETAILERS = List.of("a", "b", "c", "d");

    static final class Lcg {
        long s;

        Lcg(long seed) {
            s = seed;
        }

        double next() {
            s = (s * 1664525 + 1013904223) % 4294967296L;
            return s / 4294967296.0;
        }
    }

    record Outcome(int suboptimal, double worstGap) {
    }

    static double bruteForce(List<Map<String, Double>> costs, int maxStores, Map<String, Fees> cfg) {
        int n = costs.size();
        int[] pick = new int[n];
        double[] best = {Double.MAX_VALUE};
        rec(0, n, pick, costs, maxStores, cfg, best);
        return best[0];
    }

    static void rec(int i, int n, int[] pick, List<Map<String, Double>> costs, int maxStores, Map<String, Fees> cfg, double[] best) {
        if (i == n) {
            Map<String, Double> used = new HashMap<>();
            for (int j = 0; j < n; j++) used.merge(RETAILERS.get(pick[j]), costs.get(j).get(RETAILERS.get(pick[j])), Double::sum);
            if (used.size() > maxStores) return;
            double total = 0;
            for (var e : used.entrySet()) {
                double sub = Math.round(e.getValue() * 100) / 100.0;
                Fees f = cfg.get(e.getKey());
                double fee = f == null ? 0 : f.fee();
                if (f != null && f.freeAbove() != null && sub >= f.freeAbove()) fee = 0;
                total += sub + fee;
            }
            best[0] = Math.min(best[0], Math.round(total * 100) / 100.0);
            return;
        }
        for (int r = 0; r < RETAILERS.size(); r++) {
            pick[i] = r;
            rec(i + 1, n, pick, costs, maxStores, cfg, best);
        }
    }

    static Outcome run(String mode, int trials, long seed) {
        var rnd = new Lcg(seed);
        int suboptimal = 0;
        double worst = 0;
        for (int t = 0; t < trials; t++) {
            int n = 2 + (int) Math.floor(rnd.next() * 4);
            int k = 1 + (int) Math.floor(rnd.next() * 3);
            var costs = new ArrayList<Map<String, Double>>();
            for (int i = 0; i < n; i++) {
                var m = new LinkedHashMap<String, Double>();
                for (String r : RETAILERS) m.put(r, Math.round((1 + rnd.next() * 9) * 100) / 100.0);
                costs.add(m);
            }
            Map<String, Fees> cfg = new HashMap<>();
            if (!mode.equals("none")) {
                for (String r : RETAILERS) {
                    double fee = Math.round(rnd.next() * 4 * 100) / 100.0;
                    Double freeAbove = mode.equals("threshold") && rnd.next() < 0.6 ? (double) Math.round(5 + rnd.next() * 15) : null;
                    cfg.put(r, new Fees(fee, freeAbove, null));
                }
            }
            var choices = new ArrayList<Optimizer.Choice>();
            for (int i = 0; i < n; i++) choices.add(OptimizerTest.choice("i" + i, costs.get(i)));
            double got = Optimizer.optimize(choices, k, cfg).best().total();
            double want = bruteForce(costs, k, cfg);
            assertThat(got).as("optimizer claimed a total below the true optimum").isGreaterThanOrEqualTo(want - 0.005);
            if (got > want + 0.005) {
                suboptimal++;
                worst = Math.max(worst, got - want);
            }
        }
        return new Outcome(suboptimal, worst);
    }

    @Test
    void exactWithoutFees() {
        assertThat(run("none", 1500, 1).suboptimal()).isZero();
    }

    @Test
    void exactWithFlatFees() {
        assertThat(run("flat", 1500, 2).suboptimal()).isZero();
    }

    @Test
    void closeToOptimalWithFreeDeliveryThresholds() {
        Outcome o = run("threshold", 1500, 3);
        assertThat(o.suboptimal()).isLessThanOrEqualTo((int) (1500 * 0.02));
        assertThat(o.worstGap()).isLessThanOrEqualTo(2.0);
    }
}
