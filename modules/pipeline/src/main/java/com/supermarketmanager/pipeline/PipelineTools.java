package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.spi.AgentTools;
import com.supermarketmanager.pantry.StockService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

@Component
public class PipelineTools implements AgentTools {
    private final PipelineService pipeline;

    public PipelineTools(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @Tool(name = "pipeline_stages", description = "Lists candidate sources (price:posokanei, price:demo, supermarket:ab, pantry:catalog, basket) and resolvers (optimizer, cheapest-per-line).")
    public PipelineService.StageCatalog stages() {
        return pipeline.stages();
    }

    @Tool(name = "pipeline_run", description = """
            Builds a basket by chaining optional stages: source -> candidates -> rules -> resolve -> export.
            Source: the pantry delta (default; optional profileId/date) or a hand-made 'basket' (lines: id, label, query,
            measure kg|l|each, quantity, optional categoryId, optional product {name, brand, barcode, packSize}, optional
            offer {retailer, price, packSize}). Omit candidateSource to stop after the source (returns the basket);
            omit ruleSetId to skip rules; omit resolver to stop at candidates. Never orders anything. Saved runs get a
            runId for pipeline_apply_run.""")
    public PipelineResult run(@ToolParam(required = false, description = "hand-made basket; omit to use the pantry delta") Basket basket,
                              @ToolParam(required = false) String profileId,
                              @ToolParam(required = false, description = "ISO date for the pantry delta") String date,
                              @ToolParam(required = false, description = "candidate source id") String candidateSource,
                              @ToolParam(required = false) String ruleSetId,
                              @ToolParam(required = false, description = "optimizer or cheapest-per-line") String resolver,
                              @ToolParam(required = false) Integer maxRetailers,
                              @ToolParam(required = false, description = "restrict to one retailer id") String retailer,
                              @ToolParam(required = false, description = "include markdown checklist and store links") Boolean export) {
        var source = basket != null ? new PipelineRequest.Source("basket", null, null, basket, null)
                : new PipelineRequest.Source("pantry", profileId, date, null, null);
        var rules = ruleSetId == null ? null : new PipelineRequest.Rules(ruleSetId, null);
        var resolve = resolver == null ? null : new PipelineRequest.Resolve(resolver, maxRetailers, retailer, null);
        PipelineResult r = pipeline.run(new PipelineRequest(source, candidateSource, rules, resolve, export, null));
        // Keep tool output small: drop the (large) candidate set once a basket is resolved.
        return r.resolved() == null ? r : new PipelineResult(r.runId(), r.createdAt(), r.stages(), r.delta(), r.basket(), null,
                r.resolved(), r.checklist(), r.handoffs(), r.appliedAt());
    }

    @Tool(name = "pipeline_runs", description = "Recent saved pipeline runs (id, summary, total, appliedAt).")
    public List<RunRepository.Summary> runs() {
        return pipeline.runs(20);
    }

    @Tool(name = "pipeline_apply_run", description = """
            After the user has shopped: records a saved run's basket as bought (adds amounts to pantry stock).
            Pass line ids that were NOT bought in skipLineIds. Refuses to apply the same run twice unless force=true.""")
    public StockService.ApplyResult apply(String runId, @ToolParam(required = false) List<String> skipLineIds,
                                          @ToolParam(required = false) Boolean force) {
        return pipeline.apply(runId, skipLineIds == null ? null : new HashSet<>(skipLineIds), Boolean.TRUE.equals(force));
    }
}
