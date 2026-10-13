package com.supermarketmanager.core.spi;

import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.ResolvedBasket;

/** Chooses what to buy where. */
public interface BasketResolver {

    String id();

    String description();

    ResolvedBasket resolve(CandidateSet candidates, ResolveOptions options);
}
