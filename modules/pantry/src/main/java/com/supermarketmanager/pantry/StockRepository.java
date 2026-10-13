package com.supermarketmanager.pantry;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class StockRepository {
    private final JdbcClient jdbc;

    public StockRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<Subject, Double> amounts() {
        return jdbc.sql("SELECT subject_type, subject_id, amount FROM pantry_stock")
                .query((rs, i) -> Map.entry(new Subject(Subject.Type.of(rs.getString(1)), rs.getString(2)), rs.getDouble(3)))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public List<Map.Entry<Subject, String>> updatedAt() {
        return jdbc.sql("SELECT subject_type, subject_id, updated_at FROM pantry_stock")
                .query((rs, i) -> Map.entry(new Subject(Subject.Type.of(rs.getString(1)), rs.getString(2)), rs.getString(3)))
                .list();
    }

    public double amount(Subject s) {
        return jdbc.sql("SELECT amount FROM pantry_stock WHERE subject_type = ? AND subject_id = ?")
                .params(s.type().code(), s.id()).query(Double.class).optional().orElse(0.0);
    }

    public void put(Subject s, double amount, String at) {
        jdbc.sql("""
                INSERT INTO pantry_stock (subject_type, subject_id, amount, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (subject_type, subject_id) DO UPDATE SET amount = excluded.amount, updated_at = excluded.updated_at""")
                .params(s.type().code(), s.id(), amount, at).update();
    }

    public int delete(Subject s) {
        return jdbc.sql("DELETE FROM pantry_stock WHERE subject_type = ? AND subject_id = ?").params(s.type().code(), s.id()).update();
    }

    public void event(String at, String kind, Subject s, double delta, double resulting, Double price, String retailer,
                      String reference, String note) {
        jdbc.sql("""
                INSERT INTO pantry_stock_event (at, kind, subject_type, subject_id, delta, resulting, price, retailer, reference, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(at, kind, s.type().code(), s.id(), delta, resulting, price, retailer, reference, note).update();
    }

    public boolean referenceUsed(String reference) {
        return jdbc.sql("SELECT COUNT(*) FROM pantry_stock_event WHERE reference = ?").param(reference)
                .query(Integer.class).single() > 0;
    }

    public List<StockEvent> events(Subject subject, int limit) {
        String where = subject == null ? "" : " WHERE subject_type = :t AND subject_id = :id";
        var spec = jdbc.sql("SELECT * FROM pantry_stock_event" + where + " ORDER BY id DESC LIMIT :limit").param("limit", limit);
        if (subject != null) spec = spec.param("t", subject.type().code()).param("id", subject.id());
        return spec.query((rs, i) -> new StockEvent(rs.getLong("id"), rs.getString("at"), rs.getString("kind"),
                new Subject(Subject.Type.of(rs.getString("subject_type")), rs.getString("subject_id")),
                rs.getDouble("delta"), rs.getDouble("resulting"),
                rs.getObject("price") == null ? null : rs.getDouble("price"), rs.getString("retailer"),
                rs.getString("reference"), rs.getString("note"))).list();
    }
}
