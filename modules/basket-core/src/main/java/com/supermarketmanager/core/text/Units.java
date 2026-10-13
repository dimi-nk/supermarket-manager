package com.supermarketmanager.core.text;

import com.supermarketmanager.core.model.Measure;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pack-size parsing from provider unit fields ("g", 500) or from product names ("2x200g", "1,5 L", "600 γρ"). */
public final class Units {

    public record PackSize(Measure measure, double amount, String source) {
    }

    private record Unit(Measure measure, double factor) {
    }

    private static final Map<String, Unit> TABLE = Map.ofEntries(
            Map.entry("kg", new Unit(Measure.KG, 1)), Map.entry("kgr", new Unit(Measure.KG, 1)),
            Map.entry("kilo", new Unit(Measure.KG, 1)), Map.entry("κιλο", new Unit(Measure.KG, 1)),
            Map.entry("κιλα", new Unit(Measure.KG, 1)), Map.entry("κιλ", new Unit(Measure.KG, 1)),
            Map.entry("g", new Unit(Measure.KG, 0.001)), Map.entry("gr", new Unit(Measure.KG, 0.001)),
            Map.entry("γρ", new Unit(Measure.KG, 0.001)), Map.entry("γ", new Unit(Measure.KG, 0.001)),
            Map.entry("γραμμαρια", new Unit(Measure.KG, 0.001)),
            Map.entry("l", new Unit(Measure.L, 1)), Map.entry("lt", new Unit(Measure.L, 1)),
            Map.entry("ltr", new Unit(Measure.L, 1)), Map.entry("λιτρο", new Unit(Measure.L, 1)),
            Map.entry("λιτρα", new Unit(Measure.L, 1)), Map.entry("λιτ", new Unit(Measure.L, 1)),
            Map.entry("λτ", new Unit(Measure.L, 1)),
            Map.entry("ml", new Unit(Measure.L, 0.001)), Map.entry("μλ", new Unit(Measure.L, 0.001)),
            Map.entry("cl", new Unit(Measure.L, 0.01)),
            Map.entry("τεμ", new Unit(Measure.EACH, 1)), Map.entry("τεμαχιο", new Unit(Measure.EACH, 1)),
            Map.entry("τεμαχια", new Unit(Measure.EACH, 1)), Map.entry("pcs", new Unit(Measure.EACH, 1)),
            Map.entry("pc", new Unit(Measure.EACH, 1)), Map.entry("each", new Unit(Measure.EACH, 1)),
            Map.entry("unit", new Unit(Measure.EACH, 1)), Map.entry("units", new Unit(Measure.EACH, 1)),
            Map.entry("item", new Unit(Measure.EACH, 1)), Map.entry("pieces", new Unit(Measure.EACH, 1)));

    private static final Pattern NAME_SIZE = Pattern.compile(
            "(?:(\\d+)\\s*[xχ]\\s*)?(\\d+(?:\\.\\d+)?)\\s*(kgr|kg|κιλο|κιλα|κιλ|γρ|gr|g|γ|ml|μλ|cl|ltr|lt|λτ|λιτρο|λιτρα|λιτ|l|τεμ|pcs)(?!\\p{L})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private Units() {
    }

    public static Optional<PackSize> parsePack(String unit, Double quantity) {
        if (unit == null || quantity == null || !Double.isFinite(quantity) || quantity <= 0) return Optional.empty();
        Unit u = TABLE.get(Text.norm(unit));
        return u == null ? Optional.empty() : Optional.of(new PackSize(u.measure(), quantity * u.factor(), "api"));
    }

    /** Pulls a pack size out of a product name; the last size mentioned wins. */
    public static Optional<PackSize> parsePackFromName(String name) {
        if (name == null) return Optional.empty();
        String text = Text.norm(name.replace('×', 'x')).replaceAll("(\\d),(\\d)", "$1.$2");
        Matcher m = NAME_SIZE.matcher(text);
        PackSize last = null;
        while (m.find()) {
            double multiplier = m.group(1) != null ? Double.parseDouble(m.group(1)) : 1;
            double amount = Double.parseDouble(m.group(2));
            Unit u = TABLE.get(Text.norm(m.group(3)));
            if (u == null || amount <= 0 || multiplier <= 0) continue;
            last = new PackSize(u.measure(), multiplier * amount * u.factor(), "name");
        }
        return Optional.ofNullable(last);
    }

    /** Parses a free-text size label such as "600 γρ" or "1 κιλ" (AB's supplementary label). */
    public static Optional<PackSize> parseLabel(String label) {
        return parsePackFromName(label);
    }

    public static Optional<PackSize> resolvePack(String unit, Double quantity, String name) {
        return parsePack(unit, quantity).or(() -> parsePackFromName(name));
    }
}
