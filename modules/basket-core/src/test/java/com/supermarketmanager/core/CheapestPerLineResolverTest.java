package com.supermarketmanager.core;

import com.supermarketmanager.core.basic.CheapestPerLineResolver;
import com.supermarketmanager.core.basic.PinnedOfferSource;
import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.PinnedOffer;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.UnresolvedLine;
import com.supermarketmanager.core.spi.ResolveOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.supermarketmanager.core.TestData.cand;
import static com.supermarketmanager.core.TestData.line;
import static org.assertj.core.api.Assertions.assertThat;

class CheapestPerLineResolverTest {

    @Test
    void putsEachLineAtItsCheapestRetailer() {
        var set = new CandidateSet("t", List.of(
                new LineCandidates(line("milk", Measure.L, 2), List.of(cand("m", "X", "ab", 1, 1.2, Measure.L), cand("m", "X", "lidl", 1, 1.0, Measure.L)), null, null),
                new LineCandidates(line("feta", Measure.KG, 0.4), List.of(cand("f", "Y", "ab", 0.4, 5.0), cand("f", "Y", "lidl", 0.4, 6.0)), null, null),
                new LineCandidates(line("caviar", Measure.KG, 0.1), List.of(), null, null)), List.of());
        ResolvedBasket r = new CheapestPerLineResolver().resolve(set, ResolveOptions.defaults());
        assertThat(r.stores()).extracting("retailer").containsExactly("ab", "lidl");
        assertThat(r.total()).isEqualTo(7.0);
        assertThat(r.unresolved()).singleElement().extracting(UnresolvedLine::status).isEqualTo(UnresolvedLine.Status.UNFULFILLED);
    }

    @Test
    void handMadeBasketResolvesWithoutAnyLookup() {
        var basket = Basket.of("hand-made", List.of(
                new BasketLine("fage", "Fage 2% 500g", null, Measure.KG, 1.0, "yoghurt", List.of("yoghurt", "dairy"), null, null,
                        new PinnedOffer("ab", "AB", 2.49, 0.5), null)));
        CandidateSet set = new PinnedOfferSource().findCandidates(basket);
        ResolvedBasket r = new CheapestPerLineResolver().resolve(set, ResolveOptions.defaults());
        assertThat(r.total()).isEqualTo(4.98);
        assertThat(r.stores().getFirst().lines().getFirst().parts().getFirst().packs()).isEqualTo(2);
    }
}
