package com.supermarketmanager.pantry;

import com.supermarketmanager.core.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@DirtiesContext
class PantryIntegrationTest {
    @Autowired PantryExamples examples;
    @Autowired ProfileService profiles;
    @Autowired StockService stock;

    @Test
    void examplesDeltaVersionsAndPurchases() {
        examples.load();

        DeltaReport d = profiles.delta(null, null);
        assertThat(d.profileId()).isEqualTo("regular");
        var byKey = d.lines().stream().collect(java.util.stream.Collectors.toMap(l -> l.subject().key(), l -> l));
        // 4 tubs of yoghurt (2 kg) incl. 2 Fage packs (0.8 kg); 0.5 kg Delta in stock -> Fage 0.8 kg, generic 0.7 kg
        assertThat(byKey.get("product:fage-straggisto-2x200g").need()).isEqualTo(0.8);
        assertThat(byKey.get("category:yoghurt").need()).isEqualTo(0.7);
        assertThat(byKey.get("category:milk").need()).isEqualTo(2.0);
        assertThat(byKey.get("category:eggs").need()).isEqualTo(10.0);

        // high-protein starts next month: not in force today, in force then
        assertThatThrownBy(() -> profiles.delta("high-protein", LocalDate.now()))
                .hasMessageContaining("no targets in force");
        DeltaReport later = profiles.delta("high-protein", LocalDate.now().plusMonths(2));
        assertThat(later.lines()).anyMatch(l -> l.subject().key().equals("category:strained-yoghurt") && l.need() == 2.5);

        // record a purchase once; the same reference is refused
        var buy = List.of(new StockService.Change(Subject.category("milk"), 2, 2.4, "ab", null));
        assertThat(stock.applyPurchases(buy, "run-1", false).applied()).hasSize(1);
        assertThatThrownBy(() -> stock.applyPurchases(buy, "run-1", false)).isInstanceOf(ConflictException.class);
        assertThat(profiles.delta(null, null).lines()).anyMatch(l -> l.subject().key().equals("category:milk") && l.need() == 0);

        // a new version copies the targets in force
        TargetVersion v = profiles.createVersion("regular", LocalDate.now().plusDays(7).toString(), "next week", null);
        assertThat(v.targets()).hasSize(5);
        assertThat(stock.history(null, 10)).isNotEmpty();
    }
}
