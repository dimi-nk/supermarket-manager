package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.MixPolicy;

import java.util.List;

/**
 * One rule. {@code scope} limits it to a basket line id and/or a category (the category or anything below it);
 * no scope = every line. All applicable rules apply together.
 */
public record Rule(String id, RuleType type, Scope scope, List<String> values, Double min, Double max, MixPolicy mix,
                   Boolean enabled, String note) {

    public record Scope(String lineId, String categoryId) {
        public static final Scope ALL = new Scope(null, null);
    }

    public Rule {
        if (type == null) throw new IllegalArgumentException("Rule type is required");
        scope = scope == null ? Scope.ALL : scope;
        values = values == null ? List.of() : List.copyOf(values);
        enabled = enabled == null || enabled;
        if (min != null && max != null && min > max) throw new IllegalArgumentException("Rule min is above max");
        if (type == RuleType.MIX_POLICY && mix == null) throw new IllegalArgumentException("MIX_POLICY rules need 'mix'");
        if ((type == RuleType.DENY || type == RuleType.ALLOW || type == RuleType.RETAILER_DENY || type == RuleType.RETAILER_ALLOW)
                && values.isEmpty()) {
            throw new IllegalArgumentException(type + " rules need at least one value");
        }
        if ((type == RuleType.UNIT_PRICE || type == RuleType.PACK_PRICE || type == RuleType.PACK_SIZE) && min == null && max == null) {
            throw new IllegalArgumentException(type + " rules need min and/or max");
        }
    }

    public boolean appliesTo(BasketLine line) {
        if (!enabled) return false;
        if (scope.lineId() != null && !scope.lineId().equals(line.id())) return false;
        return scope.categoryId() == null || line.inCategory(scope.categoryId());
    }

    /** Higher = more specific: line > deeper category > shallower category > everything. */
    public int specificity(BasketLine line) {
        if (scope.lineId() != null) return 1000;
        if (scope.categoryId() != null) {
            int i = line.categoryPath().indexOf(scope.categoryId());
            return 100 - Math.max(0, i);
        }
        return 0;
    }
}
