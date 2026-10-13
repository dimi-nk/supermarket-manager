package com.supermarketmanager.pantry;

import com.supermarketmanager.core.ConflictException;
import com.supermarketmanager.core.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class StockService {
    private final StockRepository stock;
    private final CatalogRepository catalog;
    private final Clock clock;

    public StockService(StockRepository stock, CatalogRepository catalog, Optional<Clock> clock) {
        this.stock = stock;
        this.catalog = catalog;
        this.clock = clock.orElse(Clock.systemDefaultZone());
    }

    /** One purchase or adjustment. {@code price}/{@code retailer} are kept in the event log for history. */
    public record Change(Subject subject, double amount, Double price, String retailer, String note) {
    }

    public record ApplyResult(List<Change> applied, List<String> skipped) {
    }

    public List<StockEntry> list() {
        var calc = calc();
        Map<Subject, Double> amounts = stock.amounts();
        var updated = stock.updatedAt();
        return amounts.entrySet().stream().filter(e -> calc.exists(e.getKey()))
                .map(e -> new StockEntry(e.getKey(), calc.nameOf(e.getKey()), calc.measureOf(e.getKey()).code(), e.getValue(),
                        updated.stream().filter(u -> u.getKey().equals(e.getKey())).map(Map.Entry::getValue).findFirst().orElse(null)))
                .sorted(Comparator.comparing(StockEntry::name))
                .toList();
    }

    @Transactional
    public StockEntry set(Subject s, double amount, String note) {
        require(s);
        if (amount < 0) throw new IllegalArgumentException("Stock cannot be negative");
        double before = stock.amount(s);
        String now = now();
        stock.put(s, amount, now);
        stock.event(now, "set", s, amount - before, amount, null, null, null, note);
        return entry(s, amount, now);
    }

    /** Adds (positive) or consumes (negative) stock. Never goes below zero. */
    @Transactional
    public StockEntry add(Subject s, double delta, String note) {
        require(s);
        double before = stock.amount(s);
        double after = Math.max(0, Math.round((before + delta) * 1e6) / 1e6);
        String now = now();
        stock.put(s, after, now);
        stock.event(now, delta >= 0 ? "add" : "consume", s, after - before, after, null, null, null, note);
        return entry(s, after, now);
    }

    /**
     * Records purchases (e.g. from a resolved basket). {@code reference} makes the call idempotent:
     * a reference that was already applied is refused unless {@code force}.
     */
    @Transactional
    public ApplyResult applyPurchases(List<Change> changes, String reference, boolean force) {
        if (reference != null && !force && stock.referenceUsed(reference)) {
            throw new ConflictException("Purchases '" + reference + "' were already applied; pass force to apply again");
        }
        var calc = calc();
        var applied = new ArrayList<Change>();
        var skipped = new ArrayList<String>();
        String now = now();
        for (Change c : changes) {
            if (c.subject() == null || !calc.exists(c.subject())) {
                skipped.add((c.subject() == null ? "?" : c.subject().key()) + ": not a pantry category or product");
                continue;
            }
            if (c.amount() <= 0) continue;
            double before = stock.amount(c.subject());
            double after = Math.round((before + c.amount()) * 1e6) / 1e6;
            stock.put(c.subject(), after, now);
            stock.event(now, "purchase", c.subject(), c.amount(), after, c.price(), c.retailer(), reference, c.note());
            applied.add(c);
        }
        return new ApplyResult(applied, skipped);
    }

    @Transactional
    public void remove(Subject s) {
        if (stock.delete(s) == 0) throw new NotFoundException("No stock for '" + s + "'");
    }

    public List<StockEvent> history(Subject s, int limit) {
        return stock.events(s, Math.max(1, Math.min(limit, 1000)));
    }

    private StockEntry entry(Subject s, double amount, String at) {
        var calc = calc();
        return new StockEntry(s, calc.nameOf(s), calc.measureOf(s).code(), amount, at);
    }

    private void require(Subject s) {
        if (!calc().exists(s)) throw new NotFoundException("Unknown pantry subject '" + s + "'");
    }

    private DeltaCalculator calc() {
        return new DeltaCalculator(catalog.categories(), catalog.products());
    }

    private String now() {
        return Instant.now(clock).toString();
    }
}
