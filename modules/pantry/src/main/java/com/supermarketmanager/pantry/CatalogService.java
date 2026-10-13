package com.supermarketmanager.pantry;

import com.supermarketmanager.core.NotFoundException;
import com.supermarketmanager.core.text.Text;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Service
public class CatalogService {
    private final CatalogRepository repo;

    public CatalogService(CatalogRepository repo) {
        this.repo = repo;
    }

    public List<Category> categories() {
        return repo.categories();
    }

    public List<CatalogProduct> products() {
        return repo.products();
    }

    public Category category(String id) {
        return repo.category(id).orElseThrow(() -> new NotFoundException("No category '" + id + "'"));
    }

    public CatalogProduct product(String id) {
        return repo.product(id).orElseThrow(() -> new NotFoundException("No product '" + id + "'"));
    }

    @Transactional
    public Category saveCategory(Category input) {
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Category name is required");
        if (input.measure() == null) throw new IllegalArgumentException("Category measure is required (kg, l or each)");
        String id = input.id() == null || input.id().isBlank() ? Text.slugify(input.name()) : input.id();
        var c = new Category(id, input.name().trim(), blankToNull(input.parentId()), input.measure(),
                blankToNull(input.searchQuery()), blankToNull(input.notes()));
        if (c.parentId() != null) {
            category(c.parentId());
            var seen = new HashSet<String>();
            for (String p = c.parentId(); p != null; p = repo.category(p).map(Category::parentId).orElse(null)) {
                if (p.equals(id) || !seen.add(p)) throw new IllegalArgumentException("Category parent would create a cycle");
            }
        }
        repo.category(id).ifPresent(existing -> {
            if (existing.measure() != c.measure() && repo.products().stream().anyMatch(p -> p.categoryId().equals(id))) {
                throw new IllegalArgumentException("Cannot change the measure of a category that has products");
            }
        });
        repo.upsertCategory(c);
        return c;
    }

    @Transactional
    public void deleteCategory(String id) {
        if (repo.categories().stream().anyMatch(c -> id.equals(c.parentId()))) {
            throw new IllegalArgumentException("Category '" + id + "' has sub-categories; move or delete them first");
        }
        if (repo.products().stream().anyMatch(p -> p.categoryId().equals(id))) {
            throw new IllegalArgumentException("Category '" + id + "' has products; move or delete them first");
        }
        requireUnused(Subject.category(id));
        if (repo.deleteCategory(id) == 0) throw new NotFoundException("No category '" + id + "'");
    }

    @Transactional
    public CatalogProduct saveProduct(CatalogProduct input) {
        if (input.name() == null || input.name().isBlank()) throw new IllegalArgumentException("Product name is required");
        if (input.packSize() <= 0) throw new IllegalArgumentException("Product pack size must be positive");
        category(input.categoryId());
        String id = input.id() == null || input.id().isBlank() ? Text.slugify(input.name()) : input.id();
        var p = new CatalogProduct(id, input.name().trim(), blankToNull(input.brand()), input.categoryId(), input.packSize(),
                blankToNull(input.barcode()), blankToNull(input.externalSource()), blankToNull(input.externalId()),
                blankToNull(input.searchQuery()), input.privateLabel(), blankToNull(input.notes()));
        repo.upsertProduct(p);
        return p;
    }

    @Transactional
    public void deleteProduct(String id) {
        requireUnused(Subject.product(id));
        if (repo.deleteProduct(id) == 0) throw new NotFoundException("No product '" + id + "'");
    }

    private void requireUnused(Subject s) {
        var usages = repo.usages(s);
        if (!usages.isEmpty()) {
            throw new IllegalArgumentException("'" + s + "' is still used in " + String.join(" and ", usages) + "; remove it there first");
        }
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
