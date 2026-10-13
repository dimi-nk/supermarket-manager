package com.supermarketmanager.prices;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.spi.CandidateSource;
import com.supermarketmanager.core.spi.ResolveOptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DemoPipelineTest {
    @Autowired List<CandidateSource> sources;
    @Autowired StoreOptimizer optimizer;
    @Autowired PriceSettingsRepository settings;

    @Test
    void demoCandidatesFlowIntoTheOptimizer() {
        CandidateSource demo = sources.stream().filter(s -> s.id().equals("price:demo")).findFirst().orElseThrow();
        var basket = Basket.of("t", List.of(
                new BasketLine("yoghurt", "Strained yoghurt", "γιαούρτι στραγγιστό", Measure.KG, 1.5, null, null, null, null, null, null),
                new BasketLine("milk", "Milk", "γάλα φρέσκο", Measure.L, 3, null, null, null, null, null, null)));
        CandidateSet set = demo.findCandidates(basket);
        assertThat(set.lines()).allSatisfy(l -> assertThat(l.candidates()).isNotEmpty());

        ResolvedBasket one = optimizer.resolve(set, new ResolveOptions(1, null, null));
        ResolvedBasket two = optimizer.resolve(set, new ResolveOptions(2, null, null));
        assertThat(one.stores()).hasSize(1);
        assertThat(two.total()).isLessThanOrEqualTo(one.total());
        assertThat(two.unresolved()).isEmpty();
        assertThat(two.alternatives()).isNotEmpty();
        assertThat(settings.settings().maxRetailers()).isEqualTo(2);
    }
}
