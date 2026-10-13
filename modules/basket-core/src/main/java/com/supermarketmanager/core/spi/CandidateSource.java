package com.supermarketmanager.core.spi;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;

/**
 * Turns a basket into purchasable candidates. Implemented by the price-comparison module (PosoKanei, fixtures),
 * the supermarkets module (one per retailer API), and core itself ("basket": pinned offers only).
 */
public interface CandidateSource {

    /** Stable id used by the pipeline, REST and MCP, e.g. "price:posokanei", "supermarket:ab". */
    String id();

    String description();

    CandidateSet findCandidates(Basket basket);
}
