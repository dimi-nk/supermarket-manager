package com.supermarketmanager.pantry;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfileRepository {
    private final JdbcClient jdbc;

    public ProfileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Profile> profiles() {
        return jdbc.sql("SELECT * FROM pantry_profile ORDER BY active DESC, name").query((rs, i) -> new Profile(
                rs.getString("id"), rs.getString("name"), rs.getString("description"), rs.getInt("active") != 0,
                rs.getString("created_at"))).list();
    }

    public Optional<Profile> profile(String id) {
        return profiles().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void upsertProfile(Profile p) {
        jdbc.sql("""
                INSERT INTO pantry_profile (id, name, description, active, created_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, description = excluded.description""")
                .params(p.id(), p.name(), p.description(), p.active() ? 1 : 0, p.createdAt()).update();
    }

    public void activate(String id) {
        jdbc.sql("UPDATE pantry_profile SET active = CASE WHEN id = ? THEN 1 ELSE 0 END").param(id).update();
    }

    public int deleteProfile(String id) {
        return jdbc.sql("DELETE FROM pantry_profile WHERE id = ?").param(id).update();
    }

    public List<TargetVersion> versions(String profileId) {
        return jdbc.sql("SELECT * FROM pantry_target_version WHERE profile_id = ? ORDER BY effective_from")
                .param(profileId)
                .query((rs, i) -> new TargetVersion(rs.getLong("id"), rs.getString("profile_id"),
                        rs.getString("effective_from"), rs.getString("note"), rs.getString("created_at"), List.of()))
                .list().stream().map(v -> new TargetVersion(v.id(), v.profileId(), v.effectiveFrom(), v.note(),
                        v.createdAt(), targets(v.id()))).toList();
    }

    public Optional<TargetVersion> version(long id) {
        return jdbc.sql("SELECT profile_id FROM pantry_target_version WHERE id = ?").param(id).query(String.class)
                .optional().flatMap(pid -> versions(pid).stream().filter(v -> v.id() == id).findFirst());
    }

    public long insertVersion(String profileId, String effectiveFrom, String note, String createdAt) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO pantry_target_version (profile_id, effective_from, note, created_at) VALUES (?, ?, ?, ?)")
                .params(profileId, effectiveFrom, note, createdAt).update(keys, "id");
        return keys.getKey().longValue();
    }

    public void updateVersion(long id, String effectiveFrom, String note) {
        jdbc.sql("UPDATE pantry_target_version SET effective_from = ?, note = ? WHERE id = ?")
                .params(effectiveFrom, note, id).update();
    }

    public int deleteVersion(long id) {
        return jdbc.sql("DELETE FROM pantry_target_version WHERE id = ?").param(id).update();
    }

    public List<Target> targets(long versionId) {
        return jdbc.sql("SELECT * FROM pantry_target WHERE version_id = ? ORDER BY subject_type, subject_id")
                .param(versionId)
                .query((rs, i) -> new Target(new Subject(Subject.Type.of(rs.getString("subject_type")), rs.getString("subject_id")),
                        rs.getDouble("amount"), rs.getObject("pack_count") == null ? null : rs.getInt("pack_count"),
                        rs.getObject("pack_size") == null ? null : rs.getDouble("pack_size"), rs.getString("note")))
                .list();
    }

    public void replaceTargets(long versionId, List<Target> targets) {
        jdbc.sql("DELETE FROM pantry_target WHERE version_id = ?").param(versionId).update();
        for (Target t : targets) {
            jdbc.sql("""
                    INSERT INTO pantry_target (version_id, subject_type, subject_id, amount, pack_count, pack_size, note)
                    VALUES (?, ?, ?, ?, ?, ?, ?)""")
                    .params(versionId, t.subject().type().code(), t.subject().id(), t.amount(), t.packCount(), t.packSize(), t.note())
                    .update();
        }
    }
}
