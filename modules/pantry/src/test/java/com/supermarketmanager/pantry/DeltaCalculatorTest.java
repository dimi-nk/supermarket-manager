package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Measure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DeltaCalculatorTest {
    private final List<Category> cats = List.of(
            new Category("dairy", "Dairy", null, Measure.KG, null, null),
            new Category("yoghurt", "Yoghurt", "dairy", Measure.KG, "γιαούρτι", null),
            new Category("strained", "Strained yoghurt", "yoghurt", Measure.KG, null, null),
            new Category("milk", "Milk", "dairy", Measure.L, null, null));
    private final List<CatalogProduct> products = List.of(
            new CatalogProduct("fage", "Fage Total 2% 500g", "ΦΑΓΕ", "strained", 0.5, "520000", null, null, null, false, null),
            new CatalogProduct("delta", "Delta 1kg", "ΔΕΛΤΑ", "strained", 1, null, "posokanei", "pk-1", null, false, null));
    private final DeltaCalculator calc = new DeltaCalculator(cats, products);

    private DeltaReport.Line line(DeltaCalculator.Result r, String key) {
        return r.lines().stream().filter(l -> l.subject().key().equals(key)).findFirst().orElseThrow();
    }

    @Test
    void genericAndConcreteTargetsBothCountTowardsCategory() {
        // "4 tubs of any yoghurt, of which 2 Fage"; nothing in stock
        var r = calc.compute(List.of(
                new Target(Subject.category("yoghurt"), 0, 4, 0.5, null),
                new Target(Subject.product("fage"), 0, 2, 0.5, null)), Map.of(), "t");
        assertThat(line(r, "product:fage").need()).isEqualTo(1.0);
        assertThat(line(r, "category:yoghurt").fromTargetsBelow()).isEqualTo(1.0);
        assertThat(line(r, "category:yoghurt").need()).isEqualTo(1.0);
        assertThat(line(r, "category:yoghurt").suggestedPacks()).isEqualTo(2);
        assertThat(r.basket().lines()).extracting(BasketLine::id).containsExactlyInAnyOrder("category:yoghurt", "product:fage");
    }

    @Test
    void stockOfAnyProductBelowACategoryCounts() {
        var targets = List.of(new Target(Subject.category("yoghurt"), 2, null, null, null),
                new Target(Subject.product("fage"), 1, null, null, null));
        // 1 kg Delta and 0.5 kg Fage in the fridge, plus 0.2 kg loose strained yoghurt
        var stock = Map.of(Subject.product("delta"), 1.0, Subject.product("fage"), 0.5, Subject.category("strained"), 0.2);
        var r = calc.compute(targets, stock, "t");
        assertThat(line(r, "product:fage").need()).isEqualTo(0.5);
        // yoghurt: 2 - 1.7 in stock - 0.5 Fage still to buy = 0 (never negative)
        assertThat(line(r, "category:yoghurt").have()).isEqualTo(1.7);
        assertThat(line(r, "category:yoghurt").need()).isEqualTo(0.0);
        assertThat(r.basket().lines()).extracting(BasketLine::id).containsExactly("product:fage");
    }

    @Test
    void nestedCategoryTargetsAccumulate() {
        var targets = List.of(new Target(Subject.category("dairy"), 5, null, null, null),
                new Target(Subject.category("yoghurt"), 2, null, null, null),
                new Target(Subject.product("fage"), 1, null, null, null));
        var r = calc.compute(targets, Map.of(), "t");
        assertThat(line(r, "product:fage").need()).isEqualTo(1.0);
        assertThat(line(r, "category:yoghurt").need()).isEqualTo(1.0);
        assertThat(line(r, "category:dairy").need()).isEqualTo(3.0);
    }

    @Test
    void differentMeasuresAreNotSummed() {
        var r = calc.compute(List.of(new Target(Subject.category("dairy"), 1, null, null, null)),
                Map.of(Subject.category("milk"), 5.0), "t");
        assertThat(line(r, "category:dairy").need()).isEqualTo(1.0);
    }

    @Test
    void basketLinesCarryCategoryPathAndProductIdentity() {
        var r = calc.compute(List.of(new Target(Subject.product("delta"), 2, null, null, null),
                new Target(Subject.product("fage"), 1, null, null, null)), Map.of(), "t");
        BasketLine delta = r.basket().lines().stream().filter(l -> l.id().equals("product:delta")).findFirst().orElseThrow();
        assertThat(delta.categoryPath()).containsExactly("strained", "yoghurt", "dairy");
        assertThat(delta.product().source()).isEqualTo("posokanei");
        assertThat(delta.product().id()).isEqualTo("pk-1");
        BasketLine fage = r.basket().lines().stream().filter(l -> l.id().equals("product:fage")).findFirst().orElseThrow();
        assertThat(fage.product().barcode()).isEqualTo("520000");
        assertThat(fage.preferredPackSize()).isEqualTo(0.5);
    }
}
