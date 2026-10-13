package com.supermarketmanager.core.text;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text normalisation for Greek and Latin product names: lower-case, strip accents (tonos, dialytika),
 * fold final sigma, collapse punctuation. "Δέλτα", "ΔΕΛΤΑ" and "δελτα" all normalise to "δελτα".
 */
public final class Text {
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}.,%]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Text() {
    }

    public static String norm(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.toLowerCase(Locale.ROOT);
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = s.replace('ς', 'σ');
        s = NON_WORD.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    public static List<String> tokens(String input) {
        String n = norm(input);
        return n.isEmpty() ? List.of() : Arrays.asList(n.split(" "));
    }

    /**
     * True when every token of {@code needle} is a prefix of some word in {@code haystack}.
     * Word-prefix (not substring) semantics: "βιο" matches "βιολογικό" but not "αβιοτικό".
     */
    public static boolean containsAllTokens(String haystack, String needle) {
        List<String> want = tokens(needle);
        if (want.isEmpty()) return false;
        List<String> words = tokens(haystack);
        for (String t : want) {
            boolean found = false;
            for (String w : words) {
                if (w.startsWith(t)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public static double roundMoney(double value) {
        return Math.round((value + Math.ulp(value)) * 100) / 100.0;
    }

    public static String slugify(String input) {
        String s = norm(input).replace(' ', '-').replaceAll("[^\\p{L}\\p{N}-]", "");
        return s.isEmpty() ? "item" : s;
    }
}
