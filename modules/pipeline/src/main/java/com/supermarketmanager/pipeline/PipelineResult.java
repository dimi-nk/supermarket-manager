package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.pantry.DeltaReport;
import com.supermarketmanager.supermarkets.CartHandoff;

import java.util.List;

/** Output of every stage that ran. {@code runId} is set when the run was saved. */
public record PipelineResult(String runId, String createdAt, List<Stage> stages, DeltaReport delta, Basket basket,
                             CandidateSet candidates, ResolvedBasket resolved, String checklist, List<CartHandoff> handoffs,
                             String appliedAt) {

    public record Stage(String name, String detail, long millis) {
    }

    public PipelineResult withApplied(String at) {
        return new PipelineResult(runId, createdAt, stages, delta, basket, candidates, resolved, checklist, handoffs, at);
    }
}
