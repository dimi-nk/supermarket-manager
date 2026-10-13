package com.supermarketmanager.rules;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Repository
public class RuleSetRepository {
    private static final TypeReference<List<Rule>> RULES = new TypeReference<>() {
    };
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RuleSetRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<RuleSet> all() {
        return jdbc.sql("SELECT * FROM rules_rule_set ORDER BY name").query((rs, i) -> new RuleSet(
                rs.getString("id"), rs.getString("name"), rs.getString("description"),
                json.readValue(rs.getString("rules_json"), RULES), rs.getString("updated_at"))).list();
    }

    public Optional<RuleSet> find(String id) {
        return all().stream().filter(r -> r.id().equals(id)).findFirst();
    }

    public void save(RuleSet s) {
        jdbc.sql("""
                INSERT INTO rules_rule_set (id, name, description, rules_json, updated_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, description = excluded.description,
                    rules_json = excluded.rules_json, updated_at = excluded.updated_at""")
                .params(s.id(), s.name(), s.description(), json.writeValueAsString(s.rules()), s.updatedAt()).update();
    }

    public int delete(String id) {
        return jdbc.sql("DELETE FROM rules_rule_set WHERE id = ?").param(id).update();
    }
}
