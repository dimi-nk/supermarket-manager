package com.supermarketmanager.rules;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.basic.PinnedOfferSource;
import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import com.supermarketmanager.core.model.LineCandidates;
import com.supermarketmanager.core.text.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RuleService {
    private final RuleSetRepository repo;

    public RuleService(RuleSetRepository repo) {
        this.repo = repo;
    }

    public List<RuleSet> all() {
        return repo.all();
    }

    public RuleSet get(String id) {
        return repo.find(id).orElseThrow(() -> new NotFoundException("No rule set '" + id + "'"));
    }

    @Transactional
    public RuleSet save(RuleSet input) {
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Rule set name is required");
        String id = input.id() == null || input.id().isBlank() ? Text.slugify(input.name()) : input.id();
        var rules = input.rules().stream().map(r -> r.id() == null || r.id().isBlank()
                ? new Rule(UUID.randomUUID().toString().substring(0, 8), r.type(), r.scope(), r.values(), r.min(), r.max(), r.mix(), r.enabled(), r.note())
                : r).toList();
        var s = new RuleSet(id, input.name().trim(), input.description(), rules, Instant.now().toString());
        repo.save(s);
        return s;
    }

    @Transactional
    public RuleSet addRule(String ruleSetId, Rule rule) {
        RuleSet s = repo.find(ruleSetId).orElseGet(() -> new RuleSet(ruleSetId, ruleSetId, null, List.of(), null));
        var rules = new ArrayList<>(s.rules());
        rules.add(rule);
        return save(new RuleSet(s.id(), s.name(), s.description(), rules, null));
    }

    @Transactional
    public RuleSet removeRule(String ruleSetId, String ruleId) {
        RuleSet s = get(ruleSetId);
        var rules = s.rules().stream().filter(r -> !ruleId.equals(r.id())).toList();
        if (rules.size() == s.rules().size()) throw new NotFoundException("No rule '" + ruleId + "' in '" + ruleSetId + "'");
        return save(new RuleSet(s.id(), s.name(), s.description(), rules, null));
    }

    @Transactional
    public void delete(String id) {
        if (repo.delete(id) == 0) throw new NotFoundException("No rule set '" + id + "'");
    }

    /** Uses the stored set {@code ruleSetId}, or {@code inline} when given. */
    public RuleSet resolve(String ruleSetId, RuleSet inline) {
        if (inline != null) return inline;
        if (ruleSetId == null || ruleSetId.isBlank()) throw new IllegalArgumentException("Give a ruleSetId or an inline ruleSet");
        return get(ruleSetId);
    }

    public CandidateSet apply(CandidateSet candidates, RuleSet rules) {
        return RuleEngine.apply(candidates, rules);
    }

    /** Result of checking a hand-made basket (lines with pinned offers) against rules. */
    public record BasketCheck(boolean ok, List<Violation> violations, CandidateSet candidates) {
        public record Violation(String lineId, String label, List<String> reasons) {
        }
    }

    public BasketCheck checkBasket(Basket basket, RuleSet rules) {
        CandidateSet before = new PinnedOfferSource().findCandidates(basket);
        CandidateSet after = RuleEngine.apply(before, rules);
        var violations = new ArrayList<BasketCheck.Violation>();
        for (LineCandidates lc : after.lines()) {
            if (lc.line().offer() == null) continue;
            if (lc.candidates().isEmpty()) {
                violations.add(new BasketCheck.Violation(lc.line().id(), lc.line().label(),
                        List.copyOf(lc.diagnostics().rejected().keySet())));
            }
        }
        return new BasketCheck(violations.isEmpty(), violations, after);
    }
}
