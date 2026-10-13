package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.spi.CandidateSource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Candidates from your own catalog, without prices: a generic line ("yoghurt") becomes every catalog product in
 * that category or below; a concrete line becomes that product. Useful to turn a pantry delta into a concrete
 * shopping list with no price lookups at all.
 */
@Component
public class CatalogCandidateSource implements CandidateSource {
    public static final String ID = "pantry:catalog";
    private final CatalogRepository catalog;

    public CatalogCandidateSource(CatalogRepository catalog) {
        this.catalog = catalog;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String description() {
        return "Products from your pantry catalog (no prices)";
    }

    @Override
    public CandidateSet findCandidates(Basket basket) {
        var calc = new DeltaCalculator(catalog.categories(), catalog.products());
        var products = catalog.products();
        var out = new ArrayList<LineCandidates>();
        for (BasketLine line : basket.openLines()) {
            List<CatalogProduct> matches;
            if (line.product() != null) {
                matches = products.stream().filter(p -> p.id().equals(line.product().id())
                        || (p.externalId() != null && p.externalId().equals(line.product().id()))
                        || (p.barcode() != null && p.barcode().equals(line.product().barcode()))).toList();
            } else if (line.categoryId() != null) {
                matches = products.stream().filter(p -> calc.path(p.categoryId()).contains(line.categoryId())).toList();
            } else {
                matches = List.of();
            }
            var candidates = new ArrayList<Candidate>();
            var diag = new Diagnostics.Builder().searched(matches.size());
            for (CatalogProduct p : matches) {
                if (calc.measureOf(Subject.product(p.id())) != line.measure()) {
                    diag.reject("wrongMeasure");
                    continue;
                }
                candidates.add(new Candidate("catalog", p.id(), p.name(), p.brand(), p.barcode(), p.privateLabel(),
                        line.measure(), p.packSize(), "any", "Any store", null, false, null, null, null));
            }
            if (matches.isEmpty()) diag.note("no catalog products in this category");
            out.add(new LineCandidates(line, candidates, diag.build(), null));
        }
        return new CandidateSet(ID, out, List.of());
    }
}
