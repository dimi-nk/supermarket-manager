package com.supermarketmanager.prices;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.spi.ResolveOptions;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/prices")
public class PriceController {
    private final List<PriceProvider> providers;
    private final CachedSearch cache;
    private final PriceSettingsRepository settings;
    private final StoreOptimizer optimizer;

    public PriceController(List<PriceProvider> providers, CachedSearch cache, PriceSettingsRepository settings, StoreOptimizer optimizer) {
        this.providers = providers;
        this.cache = cache;
        this.settings = settings;
        this.optimizer = optimizer;
    }

    public record ProviderInfo(String id, String candidateSource, String description) {
    }

    @GetMapping("/providers")
    public List<ProviderInfo> providers() {
        return providers.stream().map(p -> new ProviderInfo(p.id(), "price:" + p.id(), p.description())).toList();
    }

    @GetMapping("/search")
    public List<Product> search(@RequestParam String provider, @RequestParam String q) {
        PriceProvider p = providers.stream().filter(x -> x.id().equals(provider)).findFirst()
                .orElseThrow(() -> new NotFoundException("No price provider '" + provider + "'"));
        int pages = settings.settings().searchPages();
        return cache.search(p.id(), q, pages, () -> p.search(q, pages));
    }

    @DeleteMapping("/cache")
    public Map<String, Integer> clearCache() {
        return Map.of("deleted", cache.clear());
    }

    @GetMapping("/settings")
    public PriceSettings settings() {
        return settings.settings();
    }

    @PutMapping("/settings")
    public PriceSettings saveSettings(@RequestBody PriceSettings s) {
        settings.save(s);
        return settings.settings();
    }

    @GetMapping("/retailers")
    public List<RetailerSetting> retailers() {
        return settings.retailers();
    }

    @PutMapping("/retailers/{retailer}")
    public RetailerSetting saveRetailer(@PathVariable String retailer, @RequestBody RetailerSetting r) {
        var s = new RetailerSetting(retailer, r.displayName(), r.fee(), r.freeAbove(), r.minBasket());
        settings.saveRetailer(s);
        return s;
    }

    @DeleteMapping("/retailers/{retailer}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRetailer(@PathVariable String retailer) {
        settings.deleteRetailer(retailer);
    }

    public record OptimizeRequest(CandidateSet candidates, Integer maxRetailers, Double packRoundingTolerance) {
    }

    /** Runs the multi-store optimizer on any candidate set. */
    @PostMapping("/optimize")
    public ResolvedBasket optimize(@RequestBody OptimizeRequest r) {
        if (r.candidates() == null) throw new IllegalArgumentException("candidates is required");
        return optimizer.resolve(r.candidates(), new ResolveOptions(r.maxRetailers(), null, r.packRoundingTolerance()));
    }
}
