package com.supermarketmanager.pipeline;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

@Repository
public class RunRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public RunRepository(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Summary(String id, String createdAt, String summary, Double total, String appliedAt) {
    }

    public void save(PipelineRequest request, PipelineResult result, String summary) {
        jdbc.sql("INSERT INTO pipeline_run (id, created_at, summary, total, request_json, result_json) VALUES (?, ?, ?, ?, ?, ?)")
                .params(result.runId(), result.createdAt(), summary, result.resolved() == null ? null : result.resolved().total(),
                        json.writeValueAsString(request), json.writeValueAsString(result))
                .update();
    }

    public List<Summary> list(int limit) {
        return jdbc.sql("SELECT id, created_at, summary, total, applied_at FROM pipeline_run ORDER BY created_at DESC LIMIT ?")
                .param(limit)
                .query((rs, i) -> new Summary(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4) == null ? null : rs.getDouble(4), rs.getString(5)))
                .list();
    }

    public Optional<PipelineResult> find(String id) {
        return jdbc.sql("SELECT result_json, applied_at FROM pipeline_run WHERE id = ?").param(id)
                .query((rs, i) -> json.readValue(rs.getString(1), PipelineResult.class).withApplied(rs.getString(2)))
                .optional();
    }

    public void markApplied(String id, String at) {
        jdbc.sql("UPDATE pipeline_run SET applied_at = ? WHERE id = ?").params(at, id).update();
    }

    public int delete(String id) {
        return jdbc.sql("DELETE FROM pipeline_run WHERE id = ?").param(id).update();
    }
}
