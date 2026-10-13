package com.supermarketmanager.core.model;

import java.util.List;

/** The candidates for one basket line, plus why others were dropped and how packs may be mixed. */
public record LineCandidates(BasketLine line, List<Candidate> candidates, Diagnostics diagnostics, MixPolicy mixPolicy) {

    public LineCandidates {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        diagnostics = diagnostics == null ? Diagnostics.empty() : diagnostics;
        mixPolicy = mixPolicy == null ? MixPolicy.SINGLE_PRODUCT : mixPolicy;
    }

    public LineCandidates with(List<Candidate> newCandidates, Diagnostics newDiagnostics, MixPolicy newPolicy) {
        return new LineCandidates(line, newCandidates, newDiagnostics, newPolicy);
    }
}
