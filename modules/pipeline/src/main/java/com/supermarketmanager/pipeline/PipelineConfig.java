package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.basic.CheapestPerLineResolver;
import com.supermarketmanager.core.basic.PinnedOfferSource;
import com.supermarketmanager.core.spi.BasketResolver;
import com.supermarketmanager.core.spi.CandidateSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Registers the dependency-free stages from basket-core. */
@Configuration
public class PipelineConfig {

    @Bean
    CandidateSource pinnedOfferSource() {
        return new PinnedOfferSource();
    }

    @Bean
    BasketResolver cheapestPerLineResolver() {
        return new CheapestPerLineResolver();
    }
}
