package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Measure;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Loads a small Greek-supermarket example so a fresh install has something to click through. */
@Service
public class PantryExamples {
    private final CatalogService catalog;
    private final ProfileService profiles;
    private final StockService stock;
    private final Clock clock;

    public PantryExamples(CatalogService catalog, ProfileService profiles, StockService stock, Optional<Clock> clock) {
        this.catalog = catalog;
        this.profiles = profiles;
        this.stock = stock;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    @Transactional
    public String load() {
        if (!profiles.profiles().isEmpty() || !catalog.categories().isEmpty()) {
            throw new IllegalStateException("Examples can only be loaded into an empty pantry");
        }
        cat("dairy", "Dairy", null, Measure.KG, null);
        cat("yoghurt", "Yoghurt", "dairy", Measure.KG, "γιαούρτι");
        cat("strained-yoghurt", "Strained yoghurt", "yoghurt", Measure.KG, "γιαούρτι στραγγιστό");
        cat("cheese", "Cheese", "dairy", Measure.KG, "τυρί");
        cat("feta", "Feta PDO", "cheese", Measure.KG, "φέτα");
        cat("milk", "Fresh milk", null, Measure.L, "γάλα φρέσκο");
        cat("eggs", "Eggs", null, Measure.EACH, "αυγά");
        cat("pasta", "Pasta", null, Measure.KG, "μακαρόνια");
        cat("olive-oil", "Olive oil", null, Measure.L, "ελαιόλαδο");

        catalog.saveProduct(new CatalogProduct("fage-straggisto-2x200g", "ΦΑΓΕ Στραγγιστό 2% 2x200g", "ΦΑΓΕ", "strained-yoghurt", 0.4,
                null, null, null, "γιαούρτι στραγγιστό", false, null));
        catalog.saveProduct(new CatalogProduct("delta-straggisto-1kg", "Δέλτα στραγγιστό 2% 1kg", "ΔΕΛΤΑ", "strained-yoghurt", 1,
                null, null, null, "δέλτα γιαούρτι στραγγιστό", false, null));

        LocalDate today = LocalDate.now(clock);
        profiles.saveProfile(new Profile("regular", "Regular week", "Everyday targets", true, null));
        profiles.createVersion("regular", today.toString(), "Initial targets", List.of(
                new Target(Subject.category("yoghurt"), 0, 4, 0.5, "4 tubs of any yoghurt in total"),
                new Target(Subject.product("fage-straggisto-2x200g"), 0, 2, 0.4, "of which 2 packs must be Fage"),
                new Target(Subject.category("milk"), 3, null, null, null),
                new Target(Subject.category("eggs"), 12, null, null, null),
                new Target(Subject.category("feta"), 0.4, null, null, null)));

        profiles.saveProfile(new Profile("high-protein", "High protein", "Diet change: more yoghurt and eggs, less pasta", false, null));
        profiles.createVersion("high-protein", today.plusMonths(1).withDayOfMonth(1).toString(), "Starts next month", List.of(
                new Target(Subject.category("strained-yoghurt"), 3, null, null, null),
                new Target(Subject.category("eggs"), 20, null, null, null),
                new Target(Subject.category("feta"), 0.4, null, null, null),
                new Target(Subject.category("milk"), 2, null, null, null)));

        stock.set(Subject.category("milk"), 1, "example");
        stock.set(Subject.category("eggs"), 2, "example");
        stock.set(Subject.product("delta-straggisto-1kg"), 0.5, "example: half a tub left");
        return "Loaded example catalog (9 categories, 2 products) and profiles 'regular' (active) and 'high-protein'.";
    }

    private void cat(String id, String name, String parent, Measure m, String query) {
        catalog.saveCategory(new Category(id, name, parent, m, query, null));
    }
}
