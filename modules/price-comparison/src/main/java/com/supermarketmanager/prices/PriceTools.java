package com.supermarketmanager.prices;

import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.spi.AgentTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PriceTools implements AgentTools {
    private final PriceController prices;

    public PriceTools(PriceController prices) {
        this.prices = prices;
    }

    @Tool(name = "prices_providers", description = "Lists multi-retailer price providers (e.g. posokanei, demo).")
    public List<PriceController.ProviderInfo> providers() {
        return prices.providers();
    }

    @Tool(name = "prices_search", description = "Searches a price provider; returns products with per-retailer offers. Greek queries work best.")
    public List<Product> search(@ToolParam(description = "provider id, e.g. posokanei or demo") String provider, String query) {
        return prices.search(provider, query);
    }

    @Tool(name = "prices_settings", description = "Optimizer defaults (maxRetailers, packRoundingTolerance, maxPriceAgeDays, searchPages) and per-retailer fees.")
    public Object settings() {
        return java.util.Map.of("settings", prices.settings(), "retailers", prices.retailers());
    }

    @Tool(name = "prices_set_retailer", description = "Sets a retailer's delivery fee, free-delivery threshold and minimum basket (EUR).")
    public RetailerSetting setRetailer(String retailer, @ToolParam(required = false) String displayName,
                                       @ToolParam(required = false) Double fee, @ToolParam(required = false) Double freeAbove,
                                       @ToolParam(required = false) Double minBasket) {
        return prices.saveRetailer(retailer, new RetailerSetting(retailer, displayName, fee == null ? 0 : fee, freeAbove, minBasket));
    }
}
