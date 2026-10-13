package com.supermarketmanager.core.fill;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.RetailerOffer;
import com.supermarketmanager.core.text.Text;
import com.supermarketmanager.core.text.Units;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared by every candidate source: turns provider products into per-line candidates, with diagnostics.
 * <p>
 * Retailer search engines are fuzzy ("φέτα" returns sliced bread, "φέτες"), so for generic lines every word of the
 * line's query must start a word of the product's brand or name. Use stems ("φρέσκ") to cover Greek inflections.
 * Concrete lines are matched by {@link ProductMatcher} instead.
 */
public final class CandidateFactory {
    private CandidateFactory() {
    }

    public static LineCandidates forLine(BasketLine line, List<Product> products) {
        var diag = new Diagnostics.Builder().searched(products.size());
        var out = new ArrayList<Candidate>();
        for (Product product : products) {
            if (line.product() != null && !ProductMatcher.matches(line.product(), product)) {
                diag.reject("notRequestedProduct");
                continue;
            }
            if (line.product() == null && !Text.containsAllTokens(
                    (product.brand() == null ? "" : product.brand() + " ") + product.name(), line.searchText())) {
                diag.reject("queryMismatch");
                continue;
            }
            Optional<Units.PackSize> pack = Units.resolvePack(product.unit(), product.unitQuantity(), product.name());
            if (pack.isEmpty()) {
                diag.reject("unknownPack");
                continue;
            }
            if (pack.get().measure() != line.measure()) {
                diag.reject("wrongMeasure");
                continue;
            }
            Double wantPack = line.product() == null ? null : line.product().packSize();
            if (wantPack != null && wantPack > 0 && Math.abs(pack.get().amount() - wantPack) / wantPack > 0.05) {
                diag.reject("otherPackSize");
                continue;
            }
            for (RetailerOffer offer : product.offers()) {
                if (!Double.isFinite(offer.price()) || offer.price() <= 0) {
                    diag.reject("invalidPrice");
                    continue;
                }
                out.add(new Candidate(product.source(), product.id(), product.name(), product.brand(), product.barcode(),
                        product.privateLabel(), line.measure(), pack.get().amount(), offer.retailer(),
                        offer.retailerName() == null ? offer.retailer() : offer.retailerName(), offer.price(),
                        offer.discount(), offer.lastUpdated(), offer.reportedUnitPrice(), offer.url()));
            }
        }
        return new LineCandidates(line, out, diag.build(), null);
    }
}
