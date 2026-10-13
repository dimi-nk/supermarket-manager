package com.supermarketmanager.core;

import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.text.Units;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UnitsTest {

    @Test
    void parsesApiUnits() {
        assertThat(Units.parsePack("g", 500.0)).get().satisfies(p -> {
            assertThat(p.measure()).isEqualTo(Measure.KG);
            assertThat(p.amount()).isCloseTo(0.5, within(1e-9));
        });
        assertThat(Units.parsePack("ΤΕΜ", 6.0)).get().extracting(Units.PackSize::measure).isEqualTo(Measure.EACH);
        assertThat(Units.parsePack("cl", 33.0)).get().extracting(Units.PackSize::amount).isEqualTo(0.33);
        assertThat(Units.parsePack("furlong", 1.0)).isEmpty();
        assertThat(Units.parsePack("kg", 0.0)).isEmpty();
    }

    @Test
    void parsesSizesFromNames() {
        assertThat(Units.parsePackFromName("ΔΕΛΤΑ ΓΙΑΟΥΡΤΙ ΣΤΡΑΓΓΙΣΤΟ 2x200g")).get()
                .satisfies(p -> assertThat(p.amount()).isCloseTo(0.4, within(1e-9)));
        assertThat(Units.parsePackFromName("ΓΑΛΑ ΦΡΕΣΚΟ 1,5 L")).get()
                .satisfies(p -> assertThat(p.amount()).isCloseTo(1.5, within(1e-9)));
        assertThat(Units.parsePackFromName("ΜΑΚΑΡΟΝΙΑ Νο10 500Γ")).get()
                .satisfies(p -> assertThat(p.amount()).isCloseTo(0.5, within(1e-9)));
        assertThat(Units.parsePackFromName("Γιαούρτι Στραγγιστό 2% Λιπαρά 3x200g")).get()
                .satisfies(p -> assertThat(p.amount()).isCloseTo(0.6, within(1e-9)));
        assertThat(Units.parsePackFromName("Αυγά Μεσαία 6 τεμ")).get().extracting(Units.PackSize::measure).isEqualTo(Measure.EACH);
        assertThat(Units.parsePackFromName("Ελαιόλαδο")).isEmpty();
    }

    @Test
    void parsesAbSupplementaryLabels() {
        assertThat(Units.parseLabel("600 γρ")).get().satisfies(p -> assertThat(p.amount()).isCloseTo(0.6, within(1e-9)));
        assertThat(Units.parseLabel("1 κιλ")).get().satisfies(p -> assertThat(p.amount()).isCloseTo(1, within(1e-9)));
        assertThat(Units.parseLabel("1 λιτ")).get().extracting(Units.PackSize::measure).isEqualTo(Measure.L);
    }

    @Test
    void apiUnitsWinOverName() {
        assertThat(Units.resolvePack("kg", 1.0, "something 500g")).get().extracting(Units.PackSize::amount).isEqualTo(1.0);
        assertThat(Units.resolvePack(null, null, "something 500g")).get().extracting(Units.PackSize::source).isEqualTo("name");
    }
}
