package com.supermarketmanager.core.model;

/** {@code packs} packs of one candidate. {@code cost} is null when the candidate has no price. */
public record FillPart(Candidate candidate, int packs, Double cost) {

    public double amount() {
        return packs * candidate.packSize();
    }
}
