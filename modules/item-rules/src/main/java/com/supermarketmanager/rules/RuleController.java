package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.CandidateSet;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/rules")
public class RuleController {
    private final RuleService rules;

    public RuleController(RuleService rules) {
        this.rules = rules;
    }

    @GetMapping("/types")
    public List<RuleType> types() {
        return Arrays.asList(RuleType.values());
    }

    @GetMapping("/sets")
    public List<RuleSet> all() {
        return rules.all();
    }

    @GetMapping("/sets/{id}")
    public RuleSet get(@PathVariable String id) {
        return rules.get(id);
    }

    @PostMapping("/sets")
    public RuleSet save(@RequestBody RuleSet s) {
        return rules.save(s);
    }

    @DeleteMapping("/sets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        rules.delete(id);
    }

    public record ApplyRequest(String ruleSetId, RuleSet ruleSet, CandidateSet candidates) {
    }

    /** Filters any candidate set (from any source) through a stored or inline rule set. */
    @PostMapping("/apply")
    public CandidateSet apply(@RequestBody ApplyRequest r) {
        if (r.candidates() == null) throw new IllegalArgumentException("candidates is required");
        return rules.apply(r.candidates(), rules.resolve(r.ruleSetId(), r.ruleSet()));
    }

    public record CheckRequest(String ruleSetId, RuleSet ruleSet, Basket basket) {
    }

    /** Checks a hand-made basket whose lines carry pinned offers. */
    @PostMapping("/check-basket")
    public RuleService.BasketCheck check(@RequestBody CheckRequest r) {
        if (r.basket() == null) throw new IllegalArgumentException("basket is required");
        return rules.checkBasket(r.basket(), rules.resolve(r.ruleSetId(), r.ruleSet()));
    }
}
