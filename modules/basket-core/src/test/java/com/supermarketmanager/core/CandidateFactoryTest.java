package com.supermarketmanager.core;

import com.supermarketmanager.core.fill.CandidateFactory;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.Product;
import com.supermarketmanager.core.model.ProductRef;
import com.supermarketmanager.core.model.RetailerOffer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateFactoryTest {
    static Product p(String id, String brand, String name, String unit, Double qty, double price) {
        return new Product("t", id, name, brand, null, unit, qty, false,
                List.of(new RetailerOffer("ab", "AB", price, null, false, null, null)));
    }

    final List<Product> search = List.of(
            p("feta", "ΔΩΔΩΝΗ", "Φέτα ΠΟΠ 400g", null, null, 5.6),
            p("bread", "ΦΟΥΡΝΙΣΜΑΤΑ", "Ψωμί Σταρένιο Φέτες 500g", null, null, 1.79),
            p("gouda", "ΝΟΥΝΟΥ", "Τυρί Gouda Φέτες 400g", "g", 400.0, 3.49),
            p("milk", "ΔΕΛΤΑ", "Γάλα Φρέσκο 1lt", null, null, 1.77),
            p("mystery", "X", "Φέτα χωρίς βάρος", null, null, 2.0));

    @Test
    void genericLinesNeedEveryQueryWordAndAMatchingMeasure() {
        var line = new BasketLine("feta", "Feta", "φέτα", Measure.KG, 0.4, null, null, null, null, null, null);
        LineCandidates lc = CandidateFactory.forLine(line, search);
        assertThat(lc.candidates()).extracting(Candidate::productId).containsExactly("feta");
        assertThat(lc.diagnostics().rejected()).containsEntry("queryMismatch", 3).containsEntry("unknownPack", 1);
    }

    @Test
    void concreteLinesUseTheProductMatcherAndPackSize() {
        var line = new BasketLine("x", null, "τυρί", Measure.KG, 0.8, null, null,
                new ProductRef(null, null, "Gouda φέτες", "Νουνού", null, 0.4, false), null, null, null);
        LineCandidates lc = CandidateFactory.forLine(line, search);
        assertThat(lc.candidates()).extracting(Candidate::productId).containsExactly("gouda");
        assertThat(lc.candidates().getFirst().packSize()).isEqualTo(0.4);
    }
}
