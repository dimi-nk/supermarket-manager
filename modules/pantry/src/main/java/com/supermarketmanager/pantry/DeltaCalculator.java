package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Basket;
import com.supermarketmanager.core.model.BasketLine;
import com.supermarketmanager.core.model.Measure;
import com.supermarketmanager.core.model.ProductRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes what is missing to reach a target pantry.
 * <p>
 * Category targets are <b>inclusive totals</b>: stock of any product or sub-category below a category counts towards it,
 * and so do more specific targets below it. With targets "yoghurt 2 kg" and "Fage 500 g x 2 (1 kg)" and nothing in stock,
 * the delta is 1 kg Fage plus 1 kg of any yoghurt. A tub of Delta yoghurt in the fridge reduces the generic 1 kg; a Fage
 * tub reduces the Fage line (and, through it, the yoghurt total).
 * <p>
 * Subjects with a different measure than the target (litres under a kg category) are not counted.
 */
public final class DeltaCalculator {
    private static final double EPS = 1e-9;

    private final Map<String, Category> categories;
    private final Map<String, CatalogProduct> products;
    private final Map<String, List<String>> children = new HashMap<>();

    public DeltaCalculator(List<Category> categories, List<CatalogProduct> products) {
        this.categories = new LinkedHashMap<>();
        categories.forEach(c -> this.categories.put(c.id(), c));
        this.products = new LinkedHashMap<>();
        products.forEach(p -> this.products.put(p.id(), p));
        for (Category c : categories) {
            if (c.parentId() != null) children.computeIfAbsent(c.parentId(), k -> new ArrayList<>()).add(c.id());
        }
    }

    public record Result(List<DeltaReport.Line> lines, Basket basket) {
    }

    public Result compute(List<Target> targets, Map<Subject, Double> stock, String basketName) {
        var ordered = targets.stream().map(Target::normalised)
                .filter(t -> exists(t.subject()))
                .sorted(Comparator.comparingInt((Target t) -> depth(t.subject())).reversed()
                        .thenComparing(t -> t.subject().key()))
                .toList();
        Map<Subject, Double> needs = new HashMap<>();
        Map<Subject, DeltaReport.Line> lines = new LinkedHashMap<>();
        for (Target t : ordered) {
            Measure m = measureOf(t.subject());
            double have = stockIn(t.subject(), stock, m);
            double below = 0;
            for (var e : needs.entrySet()) {
                if (measureOf(e.getKey()) == m && isBelow(e.getKey(), t.subject())) below += e.getValue();
            }
            double need = Math.max(0, t.amount() - have - below);
            need = Math.round(need * 1e6) / 1e6;
            needs.put(t.subject(), need);
            Double pack = t.packSize() != null ? t.packSize()
                    : t.subject().type() == Subject.Type.PRODUCT ? products.get(t.subject().id()).packSize() : null;
            Integer packs = pack == null || need <= EPS ? null : Math.max(1, (int) Math.ceil(need / pack - 0.1 - EPS));
            lines.put(t.subject(), new DeltaReport.Line(t.subject(), nameOf(t.subject()), m.code(), t.amount(),
                    round(have), round(below), need, pack, packs));
        }
        // Report in a stable, human order: category tree order, products after their category.
        var sorted = lines.values().stream().sorted(Comparator.comparing((DeltaReport.Line l) -> sortKey(l.subject()))).toList();
        var basketLines = new ArrayList<BasketLine>();
        for (DeltaReport.Line l : sorted) {
            if (l.need() <= EPS) continue;
            basketLines.add(toBasketLine(l));
        }
        return new Result(sorted, new Basket(basketName, basketLines, Map.of("origin", "pantry")));
    }

    private BasketLine toBasketLine(DeltaReport.Line l) {
        Subject s = l.subject();
        if (s.type() == Subject.Type.PRODUCT) {
            CatalogProduct p = products.get(s.id());
            boolean external = p.externalSource() != null && p.externalId() != null;
            var ref = new ProductRef(external ? p.externalId() : p.id(), external ? p.externalSource() : "catalog",
                    p.name(), p.brand(), p.barcode(), p.packSize(), p.privateLabel());
            return new BasketLine(s.key(), p.name(), p.searchQuery() != null ? p.searchQuery() : p.name(),
                    measureOf(s), l.need(), p.categoryId(), path(p.categoryId()), ref, l.packSize(), null, null);
        }
        Category c = categories.get(s.id());
        return new BasketLine(s.key(), c.name(), c.searchQuery() != null ? c.searchQuery() : c.name(), c.measure(),
                l.need(), c.id(), path(c.id()), null, l.packSize(), null, null);
    }

    public boolean exists(Subject s) {
        return s.type() == Subject.Type.CATEGORY ? categories.containsKey(s.id()) : products.containsKey(s.id());
    }

    public Measure measureOf(Subject s) {
        String cat = s.type() == Subject.Type.CATEGORY ? s.id() : products.get(s.id()).categoryId();
        return categories.get(cat).measure();
    }

    public String nameOf(Subject s) {
        if (s.type() == Subject.Type.CATEGORY) return categories.get(s.id()).name();
        CatalogProduct p = products.get(s.id());
        return p.name();
    }

    /** Category and its ancestors, most specific first. */
    public List<String> path(String categoryId) {
        var out = new ArrayList<String>();
        var seen = new HashSet<String>();
        for (String c = categoryId; c != null && seen.add(c) && categories.containsKey(c); c = categories.get(c).parentId()) out.add(c);
        return out;
    }

    private int depth(Subject s) {
        String cat = s.type() == Subject.Type.CATEGORY ? s.id() : products.get(s.id()).categoryId();
        return path(cat).size() + (s.type() == Subject.Type.PRODUCT ? 1 : 0);
    }

    private Set<String> subtree(String categoryId) {
        var out = new HashSet<String>();
        var stack = new ArrayList<>(List.of(categoryId));
        while (!stack.isEmpty()) {
            String c = stack.removeLast();
            if (out.add(c)) stack.addAll(children.getOrDefault(c, List.of()));
        }
        return out;
    }

    /** True when {@code a} is strictly below {@code b}. */
    private boolean isBelow(Subject a, Subject b) {
        if (a.equals(b) || b.type() == Subject.Type.PRODUCT) return false;
        Set<String> tree = subtree(b.id());
        return a.type() == Subject.Type.PRODUCT ? tree.contains(products.get(a.id()).categoryId()) : tree.contains(a.id());
    }

    private double stockIn(Subject s, Map<Subject, Double> stock, Measure m) {
        double total = 0;
        for (var e : stock.entrySet()) {
            Subject k = e.getKey();
            if (!exists(k) || measureOf(k) != m) continue;
            if (k.equals(s) || isBelow(k, s)) total += e.getValue();
        }
        return total;
    }

    private String sortKey(Subject s) {
        String cat = s.type() == Subject.Type.CATEGORY ? s.id() : products.get(s.id()).categoryId();
        var names = path(cat).reversed().stream().map(c -> categories.get(c).name()).toList();
        return String.join(" / ", names) + (s.type() == Subject.Type.PRODUCT ? " / ~" + nameOf(s) : "");
    }

    private static double round(double v) {
        return Math.round(v * 1e6) / 1e6;
    }
}
