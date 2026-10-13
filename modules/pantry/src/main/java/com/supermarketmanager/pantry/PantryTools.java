package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.spi.AgentTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/** Pantry management tools for agents (exposed over MCP). */
@Component
public class PantryTools implements AgentTools {
    private final CatalogService catalog;
    private final ProfileService profiles;
    private final StockService stock;

    public PantryTools(CatalogService catalog, ProfileService profiles, StockService stock) {
        this.catalog = catalog;
        this.profiles = profiles;
        this.stock = stock;
    }

    public record Overview(List<Profile> profiles, List<Category> categories, List<CatalogProduct> products) {
    }

    @Tool(name = "pantry_overview", description = """
            Lists pantry profiles (target pantries; one is active), categories (generic kinds like 'yoghurt', nested, each with a
            measure kg/l/each) and catalog products (concrete products with a pack size). Subjects elsewhere are written
            'category:<id>' or 'product:<id>'. Call this first.""")
    public Overview overview() {
        return new Overview(profiles.profiles(), catalog.categories(), catalog.products());
    }

    @Tool(name = "pantry_delta", description = """
            What is missing to reach a profile's targets: per subject target, stock (including everything below it in the
            category tree), amount covered by more specific targets, and need. Also returns 'basket', the open lines,
            which can be passed to pipeline/candidate/rules tools.""")
    public DeltaReport delta(@ToolParam(required = false, description = "profile id; default: active profile") String profileId,
                             @ToolParam(required = false, description = "ISO date; default today") String date) {
        return profiles.delta(profileId, date == null || date.isBlank() ? null : LocalDate.parse(date));
    }

    @Tool(name = "pantry_list_stock", description = "Current stock per subject, in each subject's measure.")
    public List<StockEntry> listStock() {
        return stock.list();
    }

    @Tool(name = "pantry_set_stock", description = "Sets how much of a subject is in the house now (absolute amount in its measure).")
    public StockEntry setStock(@ToolParam(description = "'category:<id>' or 'product:<id>'") String subject,
                               @ToolParam(description = "amount in kg, l or pieces") double amount,
                               @ToolParam(required = false) String note) {
        return stock.set(Subject.parse(subject), amount, note);
    }

    @Tool(name = "pantry_adjust_stock", description = "Adds (positive) or consumes (negative) stock of a subject.")
    public StockEntry adjustStock(@ToolParam(description = "'category:<id>' or 'product:<id>'") String subject,
                                  @ToolParam(description = "change in kg, l or pieces") double delta,
                                  @ToolParam(required = false) String note) {
        return stock.add(Subject.parse(subject), delta, note);
    }

    @Tool(name = "pantry_record_purchases", description = """
            Adds bought amounts to stock. Use a reference (e.g. the pipeline run id) so the same purchase is never recorded
            twice; a repeated reference is refused unless force=true.""")
    public StockService.ApplyResult recordPurchases(List<StockService.Change> purchases,
                                                    @ToolParam(required = false) String reference,
                                                    @ToolParam(required = false) Boolean force) {
        return stock.applyPurchases(purchases, reference, Boolean.TRUE.equals(force));
    }

    @Tool(name = "pantry_save_category", description = "Creates or updates a category. Children of a category count towards its targets.")
    public Category saveCategory(@ToolParam(required = false, description = "id; default: slug of name") String id,
                                 String name,
                                 @ToolParam(description = "kg, l or each") String measure,
                                 @ToolParam(required = false) String parentId,
                                 @ToolParam(required = false, description = "search text for price sources, Greek works best") String searchQuery) {
        return catalog.saveCategory(new Category(id, name, parentId, Measure.of(measure), searchQuery, null));
    }

    @Tool(name = "pantry_save_product", description = "Creates or updates a concrete catalog product in a category.")
    public CatalogProduct saveProduct(@ToolParam(required = false) String id, String name,
                                      @ToolParam(required = false) String brand, String categoryId,
                                      @ToolParam(description = "pack size in the category's measure, e.g. 0.5 for 500 g") double packSize,
                                      @ToolParam(required = false) String barcode,
                                      @ToolParam(required = false) String searchQuery) {
        return catalog.saveProduct(new CatalogProduct(id, name, brand, categoryId, packSize, barcode, null, null, searchQuery, false, null));
    }

    @Tool(name = "pantry_create_profile", description = "Creates a target pantry profile (e.g. for a diet change).")
    public Profile createProfile(String name, @ToolParam(required = false) String description,
                                 @ToolParam(required = false) Boolean activate) {
        return profiles.saveProfile(new Profile(null, name, description, Boolean.TRUE.equals(activate), null));
    }

    @Tool(name = "pantry_set_target", description = """
            Sets one target of a profile. Give amount, or packCount and packSize ('2 x 0.5 kg'). amount 0 removes it.
            With effectiveFrom, the change applies from that date: a new version is created (copying the targets in
            force then) if none starts on that date. Without it, the version in force today is edited.""")
    public TargetVersion setTarget(@ToolParam(required = false, description = "default: active profile") String profileId,
                                   @ToolParam(description = "'category:<id>' or 'product:<id>'") String subject,
                                   @ToolParam(required = false) Double amount,
                                   @ToolParam(required = false) Integer packCount,
                                   @ToolParam(required = false) Double packSize,
                                   @ToolParam(required = false, description = "ISO date") String effectiveFrom) {
        String pid = profileId == null || profileId.isBlank() ? profiles.activeProfile().id() : profileId;
        TargetVersion version;
        if (effectiveFrom != null && !effectiveFrom.isBlank()) {
            String from = LocalDate.parse(effectiveFrom).toString();
            version = profiles.versions(pid).stream().filter(v -> v.effectiveFrom().equals(from)).findFirst()
                    .orElseGet(() -> profiles.createVersion(pid, from, "created by agent", null));
        } else {
            version = profiles.versionAt(pid, LocalDate.now())
                    .orElseGet(() -> profiles.createVersion(pid, null, "created by agent", List.of()));
        }
        return profiles.setTarget(version.id(), new Target(Subject.parse(subject), amount == null ? 0 : amount, packCount, packSize, null));
    }

    @Tool(name = "pantry_versions", description = "Target versions of a profile over time (each applies from its effectiveFrom date).")
    public List<TargetVersion> versions(@ToolParam(required = false, description = "default: active profile") String profileId) {
        return profiles.versions(profileId == null || profileId.isBlank() ? profiles.activeProfile().id() : profileId);
    }
}
