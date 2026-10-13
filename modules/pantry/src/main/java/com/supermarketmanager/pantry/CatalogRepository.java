package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Measure;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CatalogRepository {
    private final JdbcClient jdbc;

    public CatalogRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Category> categories() {
        return jdbc.sql("SELECT * FROM pantry_category ORDER BY name").query((rs, i) -> new Category(
                rs.getString("id"), rs.getString("name"), rs.getString("parent_id"), Measure.of(rs.getString("measure")),
                rs.getString("search_query"), rs.getString("notes"))).list();
    }

    public Optional<Category> category(String id) {
        return categories().stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public void upsertCategory(Category c) {
        jdbc.sql("""
                INSERT INTO pantry_category (id, name, parent_id, measure, search_query, notes) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, parent_id = excluded.parent_id,
                    measure = excluded.measure, search_query = excluded.search_query, notes = excluded.notes""")
                .params(c.id(), c.name(), c.parentId(), c.measure().code(), c.searchQuery(), c.notes()).update();
    }

    public int deleteCategory(String id) {
        return jdbc.sql("DELETE FROM pantry_category WHERE id = ?").param(id).update();
    }

    public List<CatalogProduct> products() {
        return jdbc.sql("SELECT * FROM pantry_product ORDER BY name").query((rs, i) -> new CatalogProduct(
                rs.getString("id"), rs.getString("name"), rs.getString("brand"), rs.getString("category_id"),
                rs.getDouble("pack_size"), rs.getString("barcode"), rs.getString("external_source"),
                rs.getString("external_id"), rs.getString("search_query"), rs.getInt("private_label") != 0,
                rs.getString("notes"))).list();
    }

    public Optional<CatalogProduct> product(String id) {
        return products().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    public void upsertProduct(CatalogProduct p) {
        jdbc.sql("""
                INSERT INTO pantry_product (id, name, brand, category_id, pack_size, barcode, external_source, external_id,
                                            search_query, private_label, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET name = excluded.name, brand = excluded.brand, category_id = excluded.category_id,
                    pack_size = excluded.pack_size, barcode = excluded.barcode, external_source = excluded.external_source,
                    external_id = excluded.external_id, search_query = excluded.search_query,
                    private_label = excluded.private_label, notes = excluded.notes""")
                .params(p.id(), p.name(), p.brand(), p.categoryId(), p.packSize(), p.barcode(), p.externalSource(),
                        p.externalId(), p.searchQuery(), p.privateLabel() ? 1 : 0, p.notes()).update();
    }

    /** Where a subject is still used: "target" and/or "stock". */
    public java.util.List<String> usages(Subject s) {
        var out = new java.util.ArrayList<String>();
        if (jdbc.sql("SELECT COUNT(*) FROM pantry_target WHERE subject_type = ? AND subject_id = ?")
                .params(s.type().code(), s.id()).query(Integer.class).single() > 0) out.add("targets");
        if (jdbc.sql("SELECT COUNT(*) FROM pantry_stock WHERE subject_type = ? AND subject_id = ?")
                .params(s.type().code(), s.id()).query(Integer.class).single() > 0) out.add("stock");
        return out;
    }

    public int deleteProduct(String id) {
        return jdbc.sql("DELETE FROM pantry_product WHERE id = ?").param(id).update();
    }
}
