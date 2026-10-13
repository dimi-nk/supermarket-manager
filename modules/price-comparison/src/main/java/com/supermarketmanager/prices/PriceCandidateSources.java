package com.supermarketmanager.prices;

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

/** Exposes every {@link PriceProvider} as a {@link CandidateSource} with id "price:&lt;provider&gt;". */
@Configuration
public class PriceCandidateSources {

    public static final class ProviderSource implements CandidateSource {
        private final PriceProvider provider;
        private final CachedSearch cache;
        private final PriceSettingsRepository settings;

        ProviderSource(PriceProvider provider, CachedSearch cache, PriceSettingsRepository settings) {
            this.provider = provider;
            this.cache = cache;
            this.settings = settings;
        }

        @Override
        public String id() {
            return "price:" + provider.id();
        }

        @Override
        public String description() {
            return provider.description();
        }

        @Override
        public CandidateSet findCandidates(Basket basket) {
            int pages = settings.settings().searchPages();
            var lines = new ArrayList<LineCandidates>();
            var warnings = new ArrayList<String>();
            int failures = 0;
            for (BasketLine line : basket.openLines()) {
                try {
                    var products = cache.search(provider.id(), line.searchText(), pages, () -> provider.search(line.searchText(), pages));
                    lines.add(CandidateFactory.forLine(line, products));
                } catch (RuntimeException e) {
                    failures++;
                    lines.add(new LineCandidates(line, List.of(),
                            new Diagnostics.Builder().note("price lookup failed: " + e.getMessage()).build(), null));
                }
            }
            if (failures > 0 && failures == lines.size()) {
                warnings.add("Every " + provider.id() + " lookup failed - check network access to the provider.");
            }
            return new CandidateSet(id(), lines, warnings);
        }
    }

    @Bean
    CandidateSource posokaneiCandidateSource(PosoKaneiProvider p, CachedSearch c, PriceSettingsRepository s) {
        return new ProviderSource(p, c, s);
    }

    @Bean
    CandidateSource demoCandidateSource(DemoProvider p, CachedSearch c, PriceSettingsRepository s) {
        return new ProviderSource(p, c, s);
    }
}
