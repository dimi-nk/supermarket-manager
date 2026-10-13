package com.supermarketmanager.supermarkets;

import com.supermarketmanager.core.model.FillPart;
import com.supermarketmanager.core.model.ResolvedBasket;
import com.supermarketmanager.core.model.ResolvedLine;
import com.supermarketmanager.core.model.StoreBasket;
import com.supermarketmanager.core.model.UnresolvedLine;

import java.util.Locale;

/** Renders a final basket as a shopping checklist (Markdown) or CSV. Needs no retailer API. */
public final class BasketExport {
    private BasketExport() {
    }

    public static String markdown(ResolvedBasket b) {
        var sb = new StringBuilder("# Shopping list\n\n");
        if (b.total() != null) sb.append("Total: **€").append(money(b.total())).append("**\n\n");
        for (StoreBasket s : b.stores()) {
            sb.append("## ").append(s.retailerName());
            if (s.subtotal() > 0) sb.append(" - €").append(money(s.total()));
            if (s.fee() > 0) sb.append(" (incl. €").append(money(s.fee())).append(" fee)");
            sb.append("\n\n");
            for (ResolvedLine l : s.lines()) {
                for (FillPart p : l.parts()) {
                    sb.append("- [ ] ").append(p.packs()).append(" x ").append(p.candidate().name());
                    if (p.cost() != null) sb.append(" - €").append(money(p.cost()));
                    if (p.candidate().url() != null) sb.append(" ([link](").append(p.candidate().url()).append("))");
                    sb.append("  _for ").append(l.label()).append("_\n");
                }
            }
            sb.append('\n');
        }
        if (!b.unresolved().isEmpty()) {
            sb.append("## Not planned\n\n");
            for (UnresolvedLine u : b.unresolved()) sb.append("- ").append(u.label()).append(": ").append(u.reason()).append('\n');
            sb.append('\n');
        }
        if (!b.warnings().isEmpty()) {
            sb.append("## Warnings\n\n");
            b.warnings().forEach(w -> sb.append("- ").append(w).append('\n'));
        }
        return sb.toString();
    }

    public static String csv(ResolvedBasket b) {
        var sb = new StringBuilder("retailer,line,product,brand,packs,pack_size,measure,price,cost,url\n");
        for (StoreBasket s : b.stores()) {
            for (ResolvedLine l : s.lines()) {
                for (FillPart p : l.parts()) {
                    var c = p.candidate();
                    sb.append(String.join(",", q(s.retailer()), q(l.lineId()), q(c.name()), q(c.brand()),
                            String.valueOf(p.packs()), String.valueOf(c.packSize()), q(c.measure().code()),
                            c.price() == null ? "" : money(c.price()), p.cost() == null ? "" : money(p.cost()), q(c.url()))).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String money(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String q(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
