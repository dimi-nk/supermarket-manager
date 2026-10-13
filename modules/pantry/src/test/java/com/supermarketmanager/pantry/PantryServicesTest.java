package com.supermarketmanager.pantry;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.model.Measure;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:pantry-services?mode=memory&cache=shared")
@DirtiesContext
class PantryServicesTest {
    @Autowired CatalogService catalog;
    @Autowired ProfileService profiles;
    @Autowired StockService stock;

    @Test
    void catalogValidation() {
        catalog.saveCategory(new Category(null, "Dairy", null, Measure.KG, null, null));
        catalog.saveCategory(new Category(null, "Cheese", "dairy", Measure.KG, null, null));
        assertThatThrownBy(() -> catalog.saveCategory(new Category("dairy", "Dairy", "cheese", Measure.KG, null, null)))
                .hasMessageContaining("cycle");
        assertThatThrownBy(() -> catalog.saveProduct(new CatalogProduct(null, "Gouda", null, "nope", 0.4, null, null, null, null, false, null)))
                .isInstanceOf(NotFoundException.class);
        catalog.saveProduct(new CatalogProduct(null, "Gouda 400g", null, "cheese", 0.4, null, null, null, null, false, null));
        assertThatThrownBy(() -> catalog.saveCategory(new Category("cheese", "Cheese", "dairy", Measure.EACH, null, null)))
                .hasMessageContaining("measure");
        assertThatThrownBy(() -> catalog.deleteCategory("dairy")).hasMessageContaining("sub-categories");

        stock.set(Subject.product("gouda-400g"), 0.4, null);
        assertThatThrownBy(() -> catalog.deleteProduct("gouda-400g")).hasMessageContaining("still used in stock");
        stock.remove(Subject.product("gouda-400g"));
        catalog.deleteProduct("gouda-400g");
    }

    @Test
    void versionsApplyFromTheirDateAndStockNeverGoesNegative() {
        catalog.saveCategory(new Category("eggs", "Eggs", null, Measure.EACH, null, null));
        profiles.saveProfile(new Profile("p", "P", null, false, null));
        LocalDate today = LocalDate.now();
        profiles.createVersion("p", today.minusDays(10).toString(), "old", List.of(new Target(Subject.category("eggs"), 6, null, null, null)));
        profiles.createVersion("p", today.plusDays(10).toString(), "new", List.of(new Target(Subject.category("eggs"), 12, null, null, null)));
        assertThat(profiles.versionAt("p", today).orElseThrow().note()).isEqualTo("old");
        assertThat(profiles.versionAt("p", today.plusDays(11)).orElseThrow().note()).isEqualTo("new");
        assertThat(profiles.versionAt("p", today.minusDays(11))).isEmpty();
        assertThatThrownBy(() -> profiles.createVersion("p", today.minusDays(10).toString(), null, null)).hasMessageContaining("already has");

        assertThat(stock.add(Subject.category("eggs"), -5, "breakfast").amount()).isZero();
        assertThatThrownBy(() -> stock.set(Subject.category("eggs"), -1, null)).hasMessageContaining("negative");
        var applied = stock.applyPurchases(List.of(new StockService.Change(Subject.category("ghost"), 1, null, null, null)), null, false);
        assertThat(applied.skipped()).singleElement().asString().contains("ghost");
    }
}
