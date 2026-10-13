package com.supermarketmanager.pipeline;

import com.supermarketmanager.core.ConflictException;
import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.basic.CheapestPerLineResolver;
import com.supermarketmanager.core.basic.PinnedOfferSource;
import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;
import com.supermarketmanager.core.spi.BasketResolver;
import com.supermarketmanager.core.spi.CandidateSource;
import com.supermarketmanager.core.spi.ResolveOptions;
import com.supermarketmanager.pantry.DeltaReport;
import com.supermarketmanager.pantry.ProfileService;
import com.supermarketmanager.pantry.StockService;
import com.supermarketmanager.pantry.Subject;
import com.supermarketmanager.rules.RuleService;
import com.supermarketmanager.rules.RuleSet;
import com.supermarketmanager.supermarkets.BasketExport;
import com.supermarketmanager.supermarkets.CartHandoff;
import com.supermarketmanager.supermarkets.SupermarketController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Runs any prefix/subset of the stage chain. Each stage only sees the previous stage's output. */
@Service
public class PipelineService {
    private final ProfileService profiles;
    private final StockService stock;
    private final RuleService rules;
    private final List<CandidateSource> sources;
    private final List<BasketResolver> resolvers;
    private final SupermarketController supermarkets;
    private final RunRepository runs;

    public PipelineService(ProfileService profiles, StockService stock, RuleService rules, List<CandidateSource> sources,
                           List<BasketResolver> resolvers, SupermarketController supermarkets, RunRepository runs) {
        this.profiles = profiles;
        this.stock = stock;
        this.rules = rules;
        this.sources = sources;
        this.resolvers = resolvers;
        this.supermarkets = supermarkets;
        this.runs = runs;
    }

    public record StageCatalog(List<Option> candidateSources, List<Option> resolvers) {
        public record Option(String id, String description) {
        }
    }

    public StageCatalog stages() {
        return new StageCatalog(
                sources.stream().map(s -> new StageCatalog.Option(s.id(), s.description())).toList(),
                resolvers.stream().map(r -> new StageCatalog.Option(r.id(), r.description())).toList());
    }

    public CandidateSource source(String id) {
        return sources.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new NotFoundException("No candidate source '" + id + "'. Known: "
                        + sources.stream().map(CandidateSource::id).toList()));
    }

    public BasketResolver resolver(String id) {
        String want = id == null || id.isBlank() ? CheapestPerLineResolver.ID : id;
        return resolvers.stream().filter(r -> r.id().equals(want)).findFirst()
                .orElseThrow(() -> new NotFoundException("No resolver '" + want + "'. Known: "
                        + resolvers.stream().map(BasketResolver::id).toList()));
    }

    public PipelineResult run(PipelineRequest req) {
        if (req.source() == null) throw new IllegalArgumentException("source is required");
        var stages = new ArrayList<PipelineResult.Stage>();
        DeltaReport delta = null;
        Basket basket = null;
        CandidateSet candidates = null;
        long t0 = System.currentTimeMillis();

        // 1. source
        var src = req.source();
        String type = src.type() != null ? src.type()
                : src.candidateSet() != null ? "candidates" : src.basket() != null ? "basket" : "pantry";
        switch (type) {
            case "pantry" -> {
                delta = profiles.delta(src.profileId(), src.at() == null || src.at().isBlank() ? null : LocalDate.parse(src.at()));
                basket = delta.basket();
                stages.add(stage("source", "pantry '" + delta.profileId() + "' on " + delta.at() + ": " + basket.lines().size() + " lines missing", t0));
            }
            case "basket" -> {
                if (src.basket() == null) throw new IllegalArgumentException("source.basket is required for type 'basket'");
                basket = src.basket();
                stages.add(stage("source", "hand-made basket: " + basket.lines().size() + " lines", t0));
            }
            case "candidates" -> {
                if (src.candidateSet() == null) throw new IllegalArgumentException("source.candidateSet is required for type 'candidates'");
                candidates = src.candidateSet();
                stages.add(stage("source", "candidate set from '" + candidates.source() + "'", t0));
            }
            default -> throw new IllegalArgumentException("Unknown source type '" + type + "' (pantry, basket, candidates)");
        }

        // 2. candidates
        String sourceId = req.candidates();
        if (sourceId == null && candidates == null && basket != null && basket.lines().stream().anyMatch(l -> l.offer() != null)
                && (req.rules() != null || req.resolve() != null)) {
            sourceId = PinnedOfferSource.ID; // hand-made basket with prices: no lookup needed
        }
        if (sourceId != null && !sourceId.isBlank()) {
            if (basket == null) throw new IllegalArgumentException("A candidate source needs a basket (source pantry or basket)");
            long t = System.currentTimeMillis();
            candidates = source(sourceId).findCandidates(basket);
            int n = candidates.lines().stream().mapToInt(l -> l.candidates().size()).sum();
            stages.add(stage("candidates", sourceId + ": " + n + " candidates for " + candidates.lines().size() + " lines", t));
        }

        // 3. rules
        if (req.rules() != null && (req.rules().ruleSetId() != null || req.rules().ruleSet() != null)) {
            if (candidates == null) throw new IllegalArgumentException("Rules need candidates: set 'candidates' (a candidate source id)");
            long t = System.currentTimeMillis();
            RuleSet set = rules.resolve(req.rules().ruleSetId(), req.rules().ruleSet());
            int before = candidates.lines().stream().mapToInt(l -> l.candidates().size()).sum();
            candidates = rules.apply(candidates, set);
            int after = candidates.lines().stream().mapToInt(l -> l.candidates().size()).sum();
            stages.add(stage("rules", "'" + set.name() + "': kept " + after + " of " + before + " candidates", t));
        }

        // 4. resolve
        ResolvedBasket resolved = null;
        if (req.resolve() != null) {
            if (candidates == null) throw new IllegalArgumentException("Resolving needs candidates: set 'candidates' (a candidate source id)");
            long t = System.currentTimeMillis();
            var r = req.resolve();
            BasketResolver resolver = resolver(r.resolver());
            resolved = resolver.resolve(candidates, new ResolveOptions(r.maxRetailers(), r.retailer(), r.packRoundingTolerance()));
            stages.add(stage("resolve", resolver.id() + ": " + resolved.stores().size() + " store(s), total "
                    + (resolved.total() == null ? "n/a" : "€" + resolved.total()) + ", " + resolved.unresolved().size() + " unresolved", t));
        }

        // 5. export
        String checklist = null;
        List<CartHandoff> handoffs = List.of();
        if (Boolean.TRUE.equals(req.export()) && resolved != null) {
            long t = System.currentTimeMillis();
            checklist = BasketExport.markdown(resolved);
            handoffs = resolved.stores().stream().map(supermarkets::handoff).toList();
            stages.add(stage("export", "checklist + " + handoffs.size() + " store handoff(s)", t));
        }

        boolean save = req.save() == null ? resolved != null : req.save();
        String id = save ? UUID.randomUUID().toString().substring(0, 8) : null;
        var result = new PipelineResult(id, Instant.now().toString(), stages, delta, basket, candidates, resolved, checklist, handoffs, null);
        if (save) runs.save(req, result, summary(stages));
        return result;
    }

    public List<RunRepository.Summary> runs(int limit) {
        return runs.list(Math.max(1, Math.min(limit, 200)));
    }

    public PipelineResult get(String runId) {
        return runs.find(runId).orElseThrow(() -> new NotFoundException("No pipeline run '" + runId + "'"));
    }

    public void delete(String runId) {
        if (runs.delete(runId) == 0) throw new NotFoundException("No pipeline run '" + runId + "'");
    }

    /**
     * Records a saved run as bought: adds each resolved line's amount to pantry stock. Lines whose id is not a pantry
     * subject (hand-made baskets) are skipped. Refuses a second apply unless {@code force}.
     */
    @Transactional
    public StockService.ApplyResult apply(String runId, Set<String> skipLineIds, boolean force) {
        PipelineResult run = get(runId);
        if (run.appliedAt() != null && !force) throw new ConflictException("Run '" + runId + "' was already applied at " + run.appliedAt());
        if (run.resolved() == null) throw new IllegalArgumentException("Run '" + runId + "' has no resolved basket to apply");
        var changes = new ArrayList<StockService.Change>();
        var skipped = new ArrayList<String>();
        Set<String> skip = skipLineIds == null ? Set.of() : skipLineIds;
        for (StoreBasket store : run.resolved().stores()) {
            for (ResolvedLine line : store.lines()) {
                if (skip.contains(line.lineId())) {
                    skipped.add(line.lineId() + ": skipped on request");
                    continue;
                }
                if (!Subject.isKey(line.lineId())) {
                    skipped.add(line.lineId() + ": not a pantry subject");
                    continue;
                }
                changes.add(new StockService.Change(Subject.parse(line.lineId()), line.amount(), line.cost(), store.retailer(),
                        "pipeline run " + runId));
            }
        }
        var result = stock.applyPurchases(changes, "run:" + runId, force);
        runs.markApplied(runId, Instant.now().toString());
        var allSkipped = new ArrayList<>(skipped);
        allSkipped.addAll(result.skipped());
        return new StockService.ApplyResult(result.applied(), allSkipped);
    }

    private static PipelineResult.Stage stage(String name, String detail, long since) {
        return new PipelineResult.Stage(name, detail, System.currentTimeMillis() - since);
    }

    private static String summary(List<PipelineResult.Stage> stages) {
        return String.join(" → ", stages.stream().map(PipelineResult.Stage::detail).toList());
    }
}
