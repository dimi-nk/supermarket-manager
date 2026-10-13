package com.supermarketmanager.pantry;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pantry")
public class PantryController {
    private final CatalogService catalog;
    private final ProfileService profiles;
    private final StockService stock;
    private final PantryExamples examples;

    public PantryController(CatalogService catalog, ProfileService profiles, StockService stock, PantryExamples examples) {
        this.catalog = catalog;
        this.profiles = profiles;
        this.stock = stock;
        this.examples = examples;
    }

    // ---- catalog ----

    @GetMapping("/categories")
    public List<Category> categories() {
        return catalog.categories();
    }

    @PostMapping("/categories")
    public Category saveCategory(@RequestBody Category c) {
        return catalog.saveCategory(c);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String id) {
        catalog.deleteCategory(id);
    }

    @GetMapping("/products")
    public List<CatalogProduct> products() {
        return catalog.products();
    }

    @PostMapping("/products")
    public CatalogProduct saveProduct(@RequestBody CatalogProduct p) {
        return catalog.saveProduct(p);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable String id) {
        catalog.deleteProduct(id);
    }

    // ---- profiles & target versions ----

    @GetMapping("/profiles")
    public List<Profile> profiles() {
        return profiles.profiles();
    }

    @PostMapping("/profiles")
    public Profile saveProfile(@RequestBody Profile p) {
        return profiles.saveProfile(p);
    }

    @PostMapping("/profiles/{id}/activate")
    public Profile activate(@PathVariable String id) {
        profiles.activate(id);
        return profiles.profile(id);
    }

    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfile(@PathVariable String id) {
        profiles.deleteProfile(id);
    }

    public record VersionRequest(String effectiveFrom, String note, List<Target> targets) {
    }

    @GetMapping("/profiles/{id}/versions")
    public List<TargetVersion> versions(@PathVariable String id) {
        return profiles.versions(id);
    }

    @PostMapping("/profiles/{id}/versions")
    public TargetVersion createVersion(@PathVariable String id, @RequestBody VersionRequest r) {
        return profiles.createVersion(id, r.effectiveFrom(), r.note(), r.targets());
    }

    @PutMapping("/versions/{versionId}")
    public TargetVersion updateVersion(@PathVariable long versionId, @RequestBody VersionRequest r) {
        return profiles.updateVersion(versionId, r.effectiveFrom(), r.note(), r.targets());
    }

    @PutMapping("/versions/{versionId}/targets")
    public TargetVersion setTarget(@PathVariable long versionId, @RequestBody Target t) {
        return profiles.setTarget(versionId, t);
    }

    @DeleteMapping("/versions/{versionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVersion(@PathVariable long versionId) {
        profiles.deleteVersion(versionId);
    }

    /** Missing amounts (and the basket to buy them) for a profile; defaults: active profile, today. */
    @GetMapping("/delta")
    public DeltaReport delta(@RequestParam(required = false) String profileId,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate at) {
        return profiles.delta(profileId, at);
    }

    // ---- stock ----

    public record StockRequest(Double amount, Double delta, String note) {
    }

    @GetMapping("/stock")
    public List<StockEntry> stock() {
        return stock.list();
    }

    @PutMapping("/stock/{subject}")
    public StockEntry setStock(@PathVariable String subject, @RequestBody StockRequest r) {
        if (r.amount() == null) throw new IllegalArgumentException("amount is required");
        return stock.set(Subject.parse(subject), r.amount(), r.note());
    }

    @PostMapping("/stock/{subject}/add")
    public StockEntry addStock(@PathVariable String subject, @RequestBody StockRequest r) {
        if (r.delta() == null) throw new IllegalArgumentException("delta is required");
        return stock.add(Subject.parse(subject), r.delta(), r.note());
    }

    @DeleteMapping("/stock/{subject}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStock(@PathVariable String subject) {
        stock.remove(Subject.parse(subject));
    }

    public record PurchasesRequest(List<StockService.Change> purchases, String reference, boolean force) {
    }

    @PostMapping("/stock/purchases")
    public StockService.ApplyResult purchases(@RequestBody PurchasesRequest r) {
        return stock.applyPurchases(r.purchases() == null ? List.of() : r.purchases(), r.reference(), r.force());
    }

    @GetMapping("/stock/history")
    public List<StockEvent> history(@RequestParam(required = false) String subject, @RequestParam(defaultValue = "100") int limit) {
        return stock.history(subject == null || subject.isBlank() ? null : Subject.parse(subject), limit);
    }

    @PostMapping("/examples")
    public Map<String, String> loadExamples() {
        return Map.of("message", examples.load());
    }
}
