package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.fill.CandidateFactory;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Candidate;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.Product;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Runs the mapping over a real (recorded 2026-10-13) ab.gr response so API drift shows up as a test failure. */
class AbGrContractTest {

    @Test
    void mapsRecordedSearchResponse() throws Exception {
        AbGrAdapter.Response r;
        try (InputStream in = getClass().getResourceAsStream("/recorded/ab-search-giaourti-straggisto.json")) {
            r = JsonMapper.builder().build().readValue(in, AbGrAdapter.Response.class);
        }
        List<Product> products = r.data().productSearch().products().stream()
                .map(p -> AbGrAdapter.toProduct(p, "2026-10-13T00:00:00Z")).filter(Objects::nonNull).toList();
        assertThat(products).hasSize(6);
        Product own = products.stream().filter(p -> p.id().equals("7130010")).findFirst().orElseThrow();
        assertThat(own.privateLabel()).isTrue();
        assertThat(own.unit()).isEqualTo("kg");
        assertThat(own.unitQuantity()).isCloseTo(0.6, within(1e-9));
        assertThat(own.offers().getFirst().url()).startsWith("https://www.ab.gr/el/eshop/");

        var line = new BasketLine("y", "y", "γιαούρτι στραγγιστό", Measure.KG, 1.2, null, null, null, null, null, null);
        List<Candidate> cands = CandidateFactory.forLine(line, products).candidates();
        assertThat(cands).hasSize(6).allSatisfy(c -> assertThat(c.retailer()).isEqualTo("ab"));
        assertThat(cands).filteredOn(c -> c.productId().equals("7079032")).singleElement()
                .satisfies(c -> assertThat(c.unitPrice()).isCloseTo(5.35, within(1e-9)));
    }
}
