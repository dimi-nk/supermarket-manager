package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.spi.AgentTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SupermarketTools implements AgentTools {
    private final SupermarketController supermarkets;

    public SupermarketTools(SupermarketController supermarkets) {
        this.supermarkets = supermarkets;
    }

    @Tool(name = "supermarkets_list", description = "Lists supermarket adapters (direct retailer APIs) and what they can do.")
    public List<SupermarketController.AdapterInfo> list() {
        return supermarkets.list();
    }

    @Tool(name = "supermarkets_search", description = "Searches one retailer's e-shop for live products and prices (e.g. adapter 'ab'). Greek queries work best.")
    public List<Product> search(@ToolParam(description = "adapter id, e.g. ab") String adapter, String query) {
        return supermarkets.search(adapter, query);
    }
}
