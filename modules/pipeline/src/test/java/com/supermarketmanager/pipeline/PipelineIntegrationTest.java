package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.ConflictException;
import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.PinnedOffer;
import com.supermarketmanager.core.model.ProductRef;
import com.supermarketmanager.pantry.PantryExamples;
import com.supermarketmanager.pantry.ProfileService;
import com.supermarketmanager.rules.Rule;
import com.supermarketmanager.rules.RuleService;
import com.supermarketmanager.rules.RuleSet;
import com.supermarketmanager.rules.RuleType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PipelineIntegrationTest {
    @Autowired PipelineService pipeline;
    @Autowired PantryExamples examples;
    @Autowired RuleService rules;
    @Autowired ProfileService profiles;

    @BeforeAll
    void setUp() {
        examples.load();
        rules.save(new RuleSet("weekly", "Weekly", null, List.of(
                new Rule(null, RuleType.DENY, null, List.of("βιο"), null, null, null, true, null),
                new Rule(null, RuleType.UNIT_PRICE, new Rule.Scope(null, "yoghurt"), null, null, 5.0, null, true, "never above €5/kg")), null));
    }

    private static PipelineRequest.Source pantry() {
        return new PipelineRequest.Source("pantry", null, null, null, null);
    }

    /** The other example profile, at a date when it is in force (tests share one database, so avoid "regular"). */
    private static PipelineRequest.Source highProtein() {
        return new PipelineRequest.Source("pantry", "high-protein", java.time.LocalDate.now().plusMonths(2).toString(), null, null);
    }

    @Test
    void sourceOnlyReturnsTheMissingBasketWithoutSaving() {
        PipelineResult r = pipeline.run(new PipelineRequest(highProtein(), null, null, null, null, null));
        assertThat(r.runId()).isNull();
        assertThat(r.delta().profileId()).isEqualTo("high-protein");
        assertThat(r.basket().lines()).extracting(BasketLine::id).contains("category:strained-yoghurt", "category:eggs");
        assertThat(r.candidates()).isNull();
    }

    @Test
    void fullChainThenApplyOnce() {
        PipelineResult r = pipeline.run(new PipelineRequest(pantry(), "price:demo", new PipelineRequest.Rules("weekly", null),
                new PipelineRequest.Resolve("optimizer", 2, null, null), true, null));
        assertThat(r.stages()).extracting(PipelineResult.Stage::name).containsExactly("source", "candidates", "rules", "resolve", "export");
        assertThat(r.resolved().unresolved()).isEmpty();
        assertThat(r.resolved().stores()).hasSizeBetween(1, 2);
        assertThat(r.checklist()).contains("# Shopping list");
        // rules held: nothing organic, no yoghurt above 5 EUR/kg
        r.resolved().stores().forEach(s -> s.lines().forEach(l -> l.parts().forEach(p -> {
            assertThat(p.candidate().name()).doesNotContain("ΒΙΟ");
        })));
        // the concrete Fage line was filled with the Fage product only
        var fage = r.resolved().stores().stream().flatMap(s -> s.lines().stream())
                .filter(l -> l.lineId().equals("product:fage-straggisto-2x200g")).findFirst().orElseThrow();
        assertThat(fage.parts()).extracting(FillPart::candidate).extracting(Candidate::brand).containsOnly("ΦΑΓΕ");

        var applied = pipeline.apply(r.runId(), Set.of("category:feta"), false);
        assertThat(applied.applied()).isNotEmpty();
        assertThat(applied.skipped()).anyMatch(s -> s.startsWith("category:feta"));
        assertThatThrownBy(() -> pipeline.apply(r.runId(), Set.of(), false)).isInstanceOf(ConflictException.class);
        var after = profiles.delta(null, null);
        assertThat(after.lines()).filteredOn(l -> l.subject().key().equals("category:milk")).singleElement()
                .satisfies(l -> assertThat(l.need()).isZero());
        assertThat(after.lines()).filteredOn(l -> l.subject().key().equals("category:feta")).singleElement()
                .satisfies(l -> assertThat(l.need()).isEqualTo(0.4));
    }

    private final Basket handMade = Basket.of("hand-made", List.of(
            new BasketLine("fage", null, null, Measure.KG, 1, "yoghurt", List.of("yoghurt"),
                    new ProductRef(null, null, "Fage Total 2% 500g", "Fage", null, 0.5, false), null,
                    new PinnedOffer("ab", "AB", 2.9, 0.5), null),
            new BasketLine("bread", null, null, Measure.EACH, 1, null, null, null, null,
                    new PinnedOffer("lidl", "Lidl", 1.2, 1.0), null)));

    @Test
    void handMadeBasketThroughRulesOnly() {
        PipelineResult r = pipeline.run(new PipelineRequest(new PipelineRequest.Source("basket", null, null, handMade, null),
                null, new PipelineRequest.Rules("weekly", null), null, null, false));
        assertThat(r.candidates().source()).isEqualTo("basket");
        assertThat(r.candidates().lines().get(0).candidates()).isEmpty(); // 5.8 EUR/kg breaks the yoghurt cap
        assertThat(r.candidates().lines().get(0).diagnostics().rejected()).containsKey("unitPrice");
        assertThat(r.candidates().lines().get(1).candidates()).hasSize(1);
        assertThat(r.resolved()).isNull();
    }

    @Test
    void handMadeBasketStraightToFinalBasket() {
        PipelineResult r = pipeline.run(new PipelineRequest(new PipelineRequest.Source("basket", null, null, handMade, null),
                null, null, new PipelineRequest.Resolve("cheapest-per-line", null, null, null), true, null));
        assertThat(r.resolved().total()).isEqualTo(7.0);
        assertThat(r.handoffs()).hasSize(2);
        assertThat(pipeline.get(r.runId()).resolved().total()).isEqualTo(7.0);
    }

    @Test
    void catalogOnlyGivesAConcreteListWithoutPrices() {
        PipelineResult r = pipeline.run(new PipelineRequest(highProtein(), "pantry:catalog", null,
                new PipelineRequest.Resolve("cheapest-per-line", null, null, null), null, false));
        assertThat(r.resolved().total()).isNull();
        assertThat(r.resolved().stores()).singleElement().satisfies(s -> assertThat(s.retailer()).isEqualTo("any"));
        assertThat(r.resolved().unresolved()).isNotEmpty(); // milk, eggs, feta have no catalog products
    }
}
