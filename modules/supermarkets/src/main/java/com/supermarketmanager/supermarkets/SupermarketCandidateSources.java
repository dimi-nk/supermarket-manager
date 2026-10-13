package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.fill.CandidateFactory;
import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.Diagnostics;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.spi.CandidateSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/** Exposes every adapter with SEARCH as a candidate source "supermarket:&lt;id&gt;" (prices without PosoKanei). */
@Configuration
public class SupermarketCandidateSources {

    public static final class AdapterSource implements CandidateSource {
        private final SupermarketAdapter adapter;
        private final SupermarketSearch search;

        AdapterSource(SupermarketAdapter adapter, SupermarketSearch search) {
            this.adapter = adapter;
            this.search = search;
        }

        @Override
        public String id() {
            return "supermarket:" + adapter.id();
        }

        @Override
        public String description() {
            return adapter.displayName() + " e-shop search (live prices, one retailer)";
        }

        @Override
        public CandidateSet findCandidates(Basket basket) {
            var lines = new ArrayList<LineCandidates>();
            int failures = 0;
            for (BasketLine line : basket.openLines()) {
                try {
                    lines.add(CandidateFactory.forLine(line, search.search(adapter, line.searchText())));
                } catch (RuntimeException e) {
                    failures++;
                    lines.add(new LineCandidates(line, List.of(), new Diagnostics.Builder().note(adapter.id() + " lookup failed: " + e.getMessage()).build(), null));
                }
            }
            var warnings = failures > 0 && failures == lines.size()
                    ? List.of("Every " + adapter.displayName() + " lookup failed - check network access.") : List.<String>of();
            return new CandidateSet(id(), lines, warnings);
        }
    }

    @Bean
    CandidateSource abCandidateSource(AbGrAdapter ab, SupermarketSearch search) {
        return new AdapterSource(ab, search);
    }
}
