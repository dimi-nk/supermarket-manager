package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.PinnedOffer;
import com.supermarketmanager.core.model.ProductRef;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RuleServiceIntegrationTest {
    @Autowired RuleService rules;
    @Autowired RuleTools tools;

    @Test
    void storesRulesAndChecksAHandMadeBasket() {
        tools.addRule("weekly", "UNIT_PRICE", null, null, 5.0, null, "yoghurt", null, "never above 5/kg");
        tools.addRule("weekly", "deny", List.of("βιο"), null, null, null, null, null, null);
        RuleSet s = rules.get("weekly");
        assertThat(s.rules()).hasSize(2).allSatisfy(r -> assertThat(r.id()).isNotBlank());

        var basket = Basket.of("hand-made", List.of(
                new BasketLine("y1", null, null, Measure.KG, 1, "yoghurt", List.of("yoghurt"),
                        new ProductRef(null, null, "Fage Total 2% 500g", "Fage", null, 0.5, false), null,
                        new PinnedOffer("ab", "AB", 2.9, 0.5), null),
                new BasketLine("y2", null, null, Measure.KG, 1, "yoghurt", List.of("yoghurt"),
                        new ProductRef(null, null, "Δέλτα Βιολογικό 1kg", "Δέλτα", null, 1.0, false), null,
                        new PinnedOffer("ab", "AB", 4.0, 1.0), null)));
        RuleService.BasketCheck check = rules.checkBasket(basket, s);
        assertThat(check.ok()).isFalse();
        assertThat(check.violations()).extracting(RuleService.BasketCheck.Violation::lineId).containsExactlyInAnyOrder("y1", "y2");
        assertThat(check.violations().stream().filter(v -> v.lineId().equals("y1")).findFirst().orElseThrow().reasons())
                .containsExactly("unitPrice");
    }
}
