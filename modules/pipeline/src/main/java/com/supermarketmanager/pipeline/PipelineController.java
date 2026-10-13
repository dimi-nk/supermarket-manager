package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.spi.ResolveOptions;
import com.supermarketmanager.pantry.StockService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {
    private final PipelineService pipeline;

    public PipelineController(PipelineService pipeline) {
        this.pipeline = pipeline;
    }

    @GetMapping("/stages")
    public PipelineService.StageCatalog stages() {
        return pipeline.stages();
    }

    @PostMapping("/run")
    public PipelineResult run(@RequestBody PipelineRequest request) {
        return pipeline.run(request);
    }

    /** Single stage: any candidate source over any basket. */
    @PostMapping("/candidates/{sourceId}")
    public CandidateSet candidates(@PathVariable String sourceId, @RequestBody Basket basket) {
        return pipeline.source(sourceId).findCandidates(basket);
    }

    public record ResolveRequest(CandidateSet candidates, Integer maxRetailers, String retailer, Double packRoundingTolerance) {
    }

    /** Single stage: any resolver over any candidate set. */
    @PostMapping("/resolve/{resolverId}")
    public ResolvedBasket resolve(@PathVariable String resolverId, @RequestBody ResolveRequest r) {
        if (r.candidates() == null) throw new IllegalArgumentException("candidates is required");
        return pipeline.resolver(resolverId).resolve(r.candidates(), new ResolveOptions(r.maxRetailers(), r.retailer(), r.packRoundingTolerance()));
    }

    @GetMapping("/runs")
    public List<RunRepository.Summary> runs(@RequestParam(defaultValue = "50") int limit) {
        return pipeline.runs(limit);
    }

    @GetMapping("/runs/{id}")
    public PipelineResult get(@PathVariable String id) {
        return pipeline.get(id);
    }

    @DeleteMapping("/runs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        pipeline.delete(id);
    }

    public record ApplyRequest(Set<String> skip, boolean force) {
    }

    /** Records a run's basket as bought (adds to pantry stock). */
    @PostMapping("/runs/{id}/apply")
    public StockService.ApplyResult apply(@PathVariable String id, @RequestBody(required = false) ApplyRequest r) {
        return pipeline.apply(id, r == null ? Set.of() : r.skip(), r != null && r.force());
    }
}
