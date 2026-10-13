package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.StoreBasket;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/supermarkets")
public class SupermarketController {
    private final List<SupermarketAdapter> adapters;
    private final SupermarketSearch search;

    public SupermarketController(List<SupermarketAdapter> adapters, SupermarketSearch search) {
        this.adapters = adapters;
        this.search = search;
    }

    public record AdapterInfo(String id, String displayName, Set<SupermarketAdapter.Capability> capabilities, String candidateSource) {
    }

    @GetMapping
    public List<AdapterInfo> list() {
        return adapters.stream().map(a -> new AdapterInfo(a.id(), a.displayName(), a.capabilities(), "supermarket:" + a.id())).toList();
    }

    public SupermarketAdapter adapter(String id) {
        return adapters.stream().filter(a -> a.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("No supermarket adapter '" + id + "'"));
    }

    @GetMapping("/{id}/search")
    public List<Product> search(@PathVariable String id, @RequestParam String q) {
        return search.search(adapter(id), q);
    }

    /** Click-through list for one store of a final basket. Works for any retailer, adapter or not. */
    @PostMapping("/handoff")
    public CartHandoff handoff(@RequestBody StoreBasket store) {
        return adapters.stream().filter(a -> a.id().equals(store.retailer())).findFirst()
                .map(a -> a.handoff(store)).orElseGet(() -> CartHandoff.of(null, store));
    }

    @PostMapping(value = "/export", produces = {MediaType.TEXT_MARKDOWN_VALUE, "text/csv", MediaType.TEXT_PLAIN_VALUE})
    public String export(@RequestBody ResolvedBasket basket, @RequestParam(defaultValue = "markdown") String format) {
        return "csv".equalsIgnoreCase(format) ? BasketExport.csv(basket) : BasketExport.markdown(basket);
    }
}
