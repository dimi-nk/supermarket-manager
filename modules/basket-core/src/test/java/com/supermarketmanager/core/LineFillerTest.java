package com.supermarketmanager.core;

import com.supermarketmanager.core.fill.LineFiller;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.MixPolicy;
import com.supermarketmanager.core.model.ResolvedLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.supermarketmanager.core.TestData.cand;
import static com.supermarketmanager.core.TestData.line;
import static org.assertj.core.api.Assertions.assertThat;

class LineFillerTest {

    @Test
    void singleProductRoundsUpWithTolerance() {
        // need 1.08 kg of a 1 kg pack: tolerance 0.1 buys 1 pack
        ResolvedLine r = LineFiller.cheapest(line("y", Measure.KG, 1.08), List.of(cand("p1", "A", "s", 1, 4)),
                MixPolicy.SINGLE_PRODUCT, 0.1).orElseThrow();
        assertThat(r.parts()).singleElement().extracting(FillPart::packs).isEqualTo(1);
        // need 1.2 kg: 2 packs
        r = LineFiller.cheapest(line("y", Measure.KG, 1.2), List.of(cand("p1", "A", "s", 1, 4)),
                MixPolicy.SINGLE_PRODUCT, 0.1).orElseThrow();
        assertThat(r.parts().getFirst().packs()).isEqualTo(2);
        assertThat(r.cost()).isEqualTo(8.0);
    }

    @Test
    void singleProductPicksCheapestLineCost() {
        var cands = List.of(cand("big", "A", "s", 1, 3.5), cand("small", "B", "s", 0.5, 1.6));
        ResolvedLine r = LineFiller.cheapest(line("y", Measure.KG, 1.5), cands, MixPolicy.SINGLE_PRODUCT, 0).orElseThrow();
        // big: 2 x 3.5 = 7.0, small: 3 x 1.6 = 4.8
        assertThat(r.parts().getFirst().candidate().productId()).isEqualTo("small");
        assertThat(r.cost()).isEqualTo(4.8);
    }

    @Test
    void anyPolicyMixesPackSizes() {
        // need 1.5 kg: 1 kg @ 3.0 + 0.5 kg @ 1.8 = 4.8 beats 2 x 1kg = 6.0 and 3 x 0.5 = 5.4
        var cands = List.of(cand("kilo", "A", "s", 1, 3.0), cand("half", "B", "s", 0.5, 1.8));
        ResolvedLine single = LineFiller.cheapest(line("y", Measure.KG, 1.5), cands, MixPolicy.SINGLE_PRODUCT, 0).orElseThrow();
        ResolvedLine any = LineFiller.cheapest(line("y", Measure.KG, 1.5), cands, MixPolicy.ANY, 0).orElseThrow();
        assertThat(single.cost()).isEqualTo(5.4);
        assertThat(any.cost()).isEqualTo(4.8);
        assertThat(any.amount()).isEqualTo(1.5);
        assertThat(any.parts()).hasSize(2);
    }

    @Test
    void singleBrandOnlyMixesWithinABrand() {
        var cands = List.of(cand("a-kilo", "A", "s", 1, 3.0), cand("b-half", "B", "s", 0.5, 1.8),
                cand("a-half", "A", "s", 0.5, 2.2));
        ResolvedLine r = LineFiller.cheapest(line("y", Measure.KG, 1.5), cands, MixPolicy.SINGLE_BRAND, 0).orElseThrow();
        // Brand A: 3.0 + 2.2 = 5.2; brand B: 3 x 1.8 = 5.4 -> A
        assertThat(r.cost()).isEqualTo(5.2);
        assertThat(r.parts()).allSatisfy(p -> assertThat(p.candidate().brand()).isEqualTo("A"));
    }

    @Test
    void countsPiecesExactly() {
        var cands = List.of(cand("eggs6", "E", "s", 6, 2.4, Measure.EACH), cand("eggs10", "E", "s", 10, 3.6, Measure.EACH));
        ResolvedLine r = LineFiller.cheapest(line("eggs", Measure.EACH, 16), cands, MixPolicy.ANY, 0).orElseThrow();
        assertThat(r.amount()).isGreaterThanOrEqualTo(16);
        assertThat(r.cost()).isEqualTo(6.0);
    }

    @Test
    void unpricedCandidatesStillFillWithoutCost() {
        var unpriced = new com.supermarketmanager.core.model.Candidate("catalog", "p", "p", null, null, false,
                Measure.KG, 0.5, "any", "any", null, false, null, null, null);
        ResolvedLine r = LineFiller.cheapest(line("y", Measure.KG, 1), List.of(unpriced), MixPolicy.SINGLE_PRODUCT, 0.1).orElseThrow();
        assertThat(r.cost()).isNull();
        assertThat(r.parts().getFirst().packs()).isEqualTo(2);
    }
}
