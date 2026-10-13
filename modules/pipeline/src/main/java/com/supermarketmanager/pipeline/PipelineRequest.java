package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.rules.RuleSet;

/**
 * A chain of optional stages. Only {@code source} is required; the run stops after the last stage given.
 * <pre>
 * source (pantry delta | hand-made basket | ready candidate set)
 *   -> candidates (any candidate source id: price:posokanei, price:demo, supermarket:ab, pantry:catalog, basket)
 *   -> rules      (stored rule set id or inline rule set)
 *   -> resolve    (optimizer | cheapest-per-line)
 *   -> export     (checklist + per-store handoff links)
 * </pre>
 */
public record PipelineRequest(Source source, String candidates, Rules rules, Resolve resolve, Boolean export, Boolean save) {

    /** Exactly one of: pantry (profileId/at, both optional), basket, candidateSet. */
    public record Source(String type, String profileId, String at, Basket basket, CandidateSet candidateSet) {
    }

    public record Rules(String ruleSetId, RuleSet ruleSet) {
    }

    public record Resolve(String resolver, Integer maxRetailers, String retailer, Double packRoundingTolerance) {
    }
}
