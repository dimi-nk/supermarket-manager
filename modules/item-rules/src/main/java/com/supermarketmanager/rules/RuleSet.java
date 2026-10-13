package com.supermarketmanager.rules;

import java.util.List;

public record RuleSet(String id, String name, String description, List<Rule> rules, String updatedAt) {
    public RuleSet {
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
