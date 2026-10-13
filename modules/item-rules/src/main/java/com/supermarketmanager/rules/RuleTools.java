package com.supermarketmanager.rules;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.MixPolicy;
import com.supermarketmanager.core.spi.AgentTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RuleTools implements AgentTools {
    private final RuleService rules;

    public RuleTools(RuleService rules) {
        this.rules = rules;
    }

    @Tool(name = "rules_list", description = "Lists stored rule sets and their rules.")
    public List<RuleSet> list() {
        return rules.all();
    }

    @Tool(name = "rules_add_rule", description = """
            Adds a rule to a rule set (created if missing). Types: DENY/ALLOW (values: brand or name words, 'gtin:<barcode>',
            'id:<productId>'), NO_PRIVATE_LABEL, UNIT_PRICE (min/max EUR per kg|l|piece), PACK_PRICE, PACK_SIZE,
            RETAILER_DENY/RETAILER_ALLOW (values: retailer ids), ONLY_DISCOUNTED, MIX_POLICY (mix: SINGLE_PRODUCT,
            SINGLE_BRAND, ANY). Scope with categoryId (that category and below) and/or lineId; none = all lines.""")
    public RuleSet addRule(String ruleSetId, String type,
                           @ToolParam(required = false) List<String> values,
                           @ToolParam(required = false) Double min,
                           @ToolParam(required = false) Double max,
                           @ToolParam(required = false) String mix,
                           @ToolParam(required = false) String categoryId,
                           @ToolParam(required = false) String lineId,
                           @ToolParam(required = false) String note) {
        return rules.addRule(ruleSetId, new Rule(null, RuleType.valueOf(type.trim().toUpperCase()),
                new Rule.Scope(lineId, categoryId), values, min, max, MixPolicy.of(mix), true, note));
    }

    @Tool(name = "rules_remove_rule", description = "Removes a rule (by id) from a rule set.")
    public RuleSet removeRule(String ruleSetId, String ruleId) {
        return rules.removeRule(ruleSetId, ruleId);
    }

    @Tool(name = "rules_check_basket", description = """
            Checks a hand-made basket against a rule set. Basket lines need id, measure (kg|l|each), quantity, and an
            'offer' {retailer, price, packSize} plus optional 'product' {name, brand, barcode}. Returns violations per line.""")
    public RuleService.BasketCheck checkBasket(String ruleSetId, Basket basket) {
        return rules.checkBasket(basket, rules.get(ruleSetId));
    }
}
