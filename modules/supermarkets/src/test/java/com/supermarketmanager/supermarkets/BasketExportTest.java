package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;
import com.supermarketmanager.core.model.UnresolvedLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BasketExportTest {
    final Candidate feta = new Candidate("ab", "1", "Φέτα \"ΠΟΠ\", 400g", "ΔΩΔΩΝΗ", null, false, Measure.KG, 0.4, "ab", "AB",
            5.95, false, null, null, "https://www.ab.gr/p/1");
    final StoreBasket store = new StoreBasket("ab", "AB", 5.95, 2.0, 7.95, false,
            List.of(new ResolvedLine("category:feta", "Feta", Measure.KG, 0.4, 0.4, 5.95, List.of(new FillPart(feta, 1, 5.95)))));
    final ResolvedBasket basket = new ResolvedBasket("optimizer", "2026-10-13T00:00:00Z", 7.95, List.of(store),
            List.of(new UnresolvedLine("category:caviar", "Caviar", UnresolvedLine.Status.UNFULFILLED, "no products found")),
            List.of(), List.of("Price is 9 days old."));

    @Test
    void markdownChecklistHasStoresLinksUnresolvedAndWarnings() {
        String md = BasketExport.markdown(basket);
        assertThat(md).contains("Total: **€7.95**", "## AB - €7.95 (incl. €2.00 fee)",
                "- [ ] 1 x Φέτα \"ΠΟΠ\", 400g - €5.95 ([link](https://www.ab.gr/p/1))", "## Not planned", "Caviar: no products found",
                "## Warnings");
    }

    @Test
    void csvQuotesTextFields() {
        String csv = BasketExport.csv(basket);
        assertThat(csv.lines().toList()).containsExactly(
                "retailer,line,product,brand,packs,pack_size,measure,price,cost,url",
                "\"ab\",\"category:feta\",\"Φέτα \"\"ΠΟΠ\"\", 400g\",\"ΔΩΔΩΝΗ\",1,0.4,\"kg\",5.95,5.95,\"https://www.ab.gr/p/1\"");
    }

    @Test
    void handoffListsEveryPart() {
        CartHandoff h = CartHandoff.of(null, store);
        assertThat(h.items()).singleElement().satisfies(i -> {
            assertThat(i.packs()).isEqualTo(1);
            assertThat(i.url()).isEqualTo("https://www.ab.gr/p/1");
        });
    }
}
