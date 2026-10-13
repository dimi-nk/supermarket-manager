package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.MixPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.supermarketmanager.core.TestData.cand;
import static com.supermarketmanager.core.TestData.line;
import static org.assertj.core.api.Assertions.assertThat;

class RuleEngineTest {
    private final BasketLine yoghurt = line("category:yoghurt", Measure.KG, 1, "strained", List.of("strained", "yoghurt", "dairy"));
    private final BasketLine milk = line("category:milk", Measure.L, 2, "milk", List.of("milk"));

    private final Candidate fage = new Candidate("t", "p1", "ΦΑΓΕ TOTAL 2% 500Γ", "ΦΑΓΕ", "520001", false, Measure.KG, 0.5,
            "ab", "AB", 2.4, false, null, null, null);
    private final Candidate deltaBio = new Candidate("t", "p2", "ΔΕΛΤΑ ΒΙΟΛΟΓΙΚΟ ΓΙΑΟΥΡΤΙ 1KG", "ΔΕΛΤΑ", null, false, Measure.KG, 1,
            "sklavenitis", "Sklavenitis", 4.2, true, null, null, null);
    private final Candidate own = new Candidate("t", "p3", "ΑΒ ΕΠΙΛΟΓΗ ΓΙΑΟΥΡΤΙ 1KG", "ΑΒ ΕΠΙΛΟΓΗ", null, true, Measure.KG, 1,
            "ab", "AB", 3.1, false, null, null, null);
    private final Candidate expensive = cand("lux", "LUX", "ab", 0.15, 1.5);

    private CandidateSet set() {
        return new CandidateSet("t", List.of(
                new LineCandidates(yoghurt, List.of(fage, deltaBio, own, expensive), null, null),
                new LineCandidates(milk, List.of(cand("m", "X", "lidl", 1, 1, Measure.L)), null, null)), List.of());
    }

    private LineCandidates apply(Rule... rules) {
        return RuleEngine.apply(set(), new RuleSet("s", "s", null, List.of(rules), null)).lines().getFirst();
    }

    private static Rule rule(RuleType t, Rule.Scope scope, List<String> values, Double min, Double max, MixPolicy mix) {
        return new Rule("r", t, scope, values, min, max, mix, true, null);
    }

    @Test
    void denyBeatsAllowAndMatchesWordPrefixes() {
        var lc = apply(rule(RuleType.ALLOW, null, List.of("δέλτα", "φαγε"), null, null, null),
                rule(RuleType.DENY, null, List.of("βιο"), null, null, null));
        assertThat(lc.candidates()).extracting(Candidate::productId).containsExactly("p1");
        assertThat(lc.diagnostics().rejected()).containsEntry("denied", 1).containsEntry("notAllowed", 2);
    }

    @Test
    void exactMatchersAndPrivateLabel() {
        assertThat(apply(rule(RuleType.ALLOW, null, List.of("gtin:520001"), null, null, null)).candidates())
                .extracting(Candidate::productId).containsExactly("p1");
        assertThat(apply(new Rule("r", RuleType.NO_PRIVATE_LABEL, null, null, null, null, null, true, null)).candidates())
                .extracting(Candidate::productId).doesNotContain("p3");
    }

    @Test
    void unitPriceRangeReportsClosestRejected() {
        // max 5 EUR/kg: fage 4.8, delta 4.2, own 3.1 pass; lux 10/kg fails
        var lc = apply(rule(RuleType.UNIT_PRICE, null, null, null, 5.0, null));
        assertThat(lc.candidates()).extracting(Candidate::productId).containsExactly("p1", "p2", "p3");
        assertThat(lc.diagnostics().closestRejected().unitPrice()).isEqualTo(10.0);
        // min 4: drops own-brand at 3.1
        assertThat(apply(rule(RuleType.UNIT_PRICE, null, null, 4.0, null, null)).candidates()).hasSize(3);
    }

    @Test
    void scopesLimitRulesToCategoriesAndLines() {
        Rule onlyDairy = rule(RuleType.RETAILER_DENY, new Rule.Scope(null, "dairy"), List.of("ab"), null, null, null);
        CandidateSet out = RuleEngine.apply(set(), new RuleSet("s", "s", null, List.of(onlyDairy), null));
        assertThat(out.lines().get(0).candidates()).extracting(Candidate::retailer).containsOnly("sklavenitis");
        assertThat(out.lines().get(1).candidates()).hasSize(1); // milk untouched
    }

    @Test
    void mostSpecificMixPolicyWins() {
        var lc = apply(rule(RuleType.MIX_POLICY, null, null, null, null, MixPolicy.ANY),
                rule(RuleType.MIX_POLICY, new Rule.Scope(null, "yoghurt"), null, null, null, MixPolicy.SINGLE_BRAND));
        assertThat(lc.mixPolicy()).isEqualTo(MixPolicy.SINGLE_BRAND);
    }

    @Test
    void retailerAllowAndDiscountOnly() {
        assertThat(apply(rule(RuleType.RETAILER_ALLOW, null, List.of("Sklavenitis"), null, null, null)).candidates())
                .extracting(Candidate::productId).containsExactly("p2");
        assertThat(apply(new Rule("r", RuleType.ONLY_DISCOUNTED, null, null, null, null, null, true, null)).candidates())
                .extracting(Candidate::productId).containsExactly("p2");
    }
}
