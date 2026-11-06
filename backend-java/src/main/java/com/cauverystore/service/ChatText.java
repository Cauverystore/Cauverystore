package com.cauverystore.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading shoppers' chat messages without an outside AI: spelling slips, plurals, everyday
 * synonyms, "the second one", and quantities like "add two".
 */
final class ChatText {

    private ChatText() {
    }

    /** Everyday words shoppers use for the same thing. Each word maps to its alternatives. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("tv", List.of("television")),
            Map.entry("television", List.of("tv")),
            Map.entry("mobile", List.of("phone", "smartphone")),
            Map.entry("cellphone", List.of("phone", "mobile")),
            Map.entry("phone", List.of("mobile", "smartphone")),
            Map.entry("smartphone", List.of("phone", "mobile")),
            Map.entry("tshirt", List.of("t-shirt", "tee")),
            Map.entry("tee", List.of("t-shirt", "tshirt")),
            Map.entry("pant", List.of("trouser", "pants")),
            Map.entry("trouser", List.of("pant", "pants")),
            Map.entry("chappal", List.of("slipper", "sandal")),
            Map.entry("slipper", List.of("chappal", "sandal")),
            Map.entry("kurti", List.of("kurta")),
            Map.entry("kurta", List.of("kurti")),
            Map.entry("sari", List.of("saree")),
            Map.entry("saree", List.of("sari")),
            Map.entry("dhoti", List.of("veshti")),
            Map.entry("veshti", List.of("dhoti")),
            Map.entry("towel", List.of("napkin")),
            Map.entry("bedsheet", List.of("bed sheet")),
            Map.entry("earphone", List.of("earphones", "headphone", "earbuds")),
            Map.entry("headphone", List.of("headphones", "earphone")),
            Map.entry("laptop", List.of("notebook")),
            Map.entry("fridge", List.of("refrigerator")),
            Map.entry("ac", List.of("air conditioner")),
            Map.entry("specs", List.of("spectacles", "glasses")),
            Map.entry("bag", List.of("backpack", "handbag")),
            Map.entry("shoe", List.of("shoes", "footwear")),
            Map.entry("watch", List.of("watches", "smartwatch"))
    );

    /** Two-letter words that still mean a product. */
    static final Set<String> SHORT_PRODUCT_WORDS = Set.of("tv", "ac");

    /** The word itself, its singular form, and common synonyms. */
    static List<String> variants(String word) {
        Set<String> out = new LinkedHashSet<>();
        out.add(word);
        String stem = singular(word);
        out.add(stem);
        out.addAll(SYNONYMS.getOrDefault(word, List.of()));
        out.addAll(SYNONYMS.getOrDefault(stem, List.of()));
        return new ArrayList<>(out);
    }

    static String singular(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 4 && (w.endsWith("ches") || w.endsWith("shes") || w.endsWith("xes") || w.endsWith("sses"))) {
            return w.substring(0, w.length() - 2);
        }
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    /** True when two words differ only by a small typo (one slip for short words, two for long). */
    static boolean closeEnough(String typed, String word) {
        if (typed.length() < 4 || word.length() < 4) return false;
        int allowed = typed.length() >= 8 ? 2 : 1;
        if (Math.abs(typed.length() - word.length()) > allowed) return false;
        return editDistance(typed, word, allowed) <= allowed;
    }

    /** Damerau-Levenshtein distance (swapped letters count as one slip), stopping early past max. */
    static int editDistance(String a, String b, int max) {
        int n = a.length();
        int m = b.length();
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            int rowMin = Integer.MAX_VALUE;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, d[i - 2][j - 2] + 1);
                }
                d[i][j] = v;
                rowMin = Math.min(rowMin, v);
            }
            if (rowMin > max) return max + 1;
        }
        return d[n][m];
    }

    private static final Map<String, Integer> ORDINALS = Map.ofEntries(
            Map.entry("first", 0), Map.entry("1st", 0),
            Map.entry("second", 1), Map.entry("2nd", 1),
            Map.entry("third", 2), Map.entry("3rd", 2),
            Map.entry("fourth", 3), Map.entry("4th", 3),
            Map.entry("fifth", 4), Map.entry("5th", 4),
            Map.entry("sixth", 5), Map.entry("6th", 5)
    );

    private static final Pattern ORDINAL = Pattern.compile(
            "\\b(first|1st|second|2nd|third|3rd|fourth|4th|fifth|5th|sixth|6th|last)\\b");

    /**
     * Position meant by "the second one", "the last one" and so on, or -1. {@code size} is how
     * many items were listed, needed for "last".
     */
    static int ordinal(String msg, int size) {
        Matcher m = ORDINAL.matcher(msg);
        if (!m.find()) return -1;
        if ("last".equals(m.group(1))) return size - 1;
        return ORDINALS.get(m.group(1));
    }

    private static final Pattern POINTER = Pattern.compile(
            "\\b(it|this|that|this one|that one|same|the same|these|those|them)\\b");

    /** "add it", "is this in stock": the shopper points at something already shown. */
    static boolean pointsBack(String msg) {
        return POINTER.matcher(msg).find();
    }

    private static final Map<String, Integer> NUMBER_WORDS = Map.of(
            "one", 1, "two", 2, "three", 3, "four", 4, "five", 5,
            "six", 6, "seven", 7, "eight", 8, "nine", 9, "ten", 10);

    private static final Pattern QTY_AFTER_VERB = Pattern.compile(
            "\\b(?:add|put|buy|get|move)\\s+(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\\b(?!\\s*(?:kg|g|gm|gms|ml|l|ltr|litre|liter|inch|cm|mm|m)\\b)");
    private static final Pattern QTY_UNIT = Pattern.compile(
            "\\b(\\d{1,2}|one|two|three|four|five|six|seven|eight|nine|ten)\\s*(?:x|pcs|pieces|piece|nos|units|qty|packs|packets|quantity)\\b");
    private static final Pattern QTY_WORD = Pattern.compile("\\b(?:qty|quantity)\\s*(?:of|:)?\\s*(\\d{1,2})\\b");

    /** How many the shopper asked for ("add 2", "3 pieces", "quantity 4"), else 1. At most 10. */
    static int quantity(String msg) {
        for (Pattern p : List.of(QTY_WORD, QTY_UNIT, QTY_AFTER_VERB)) {
            Matcher m = p.matcher(msg);
            if (m.find()) {
                String g = m.group(1);
                int n = NUMBER_WORDS.containsKey(g) ? NUMBER_WORDS.get(g) : Integer.parseInt(g);
                return Math.max(1, Math.min(10, n));
            }
        }
        return 1;
    }

    private static final Pattern ORDER_NUMBER = Pattern.compile(
            "(?:\\border\\s*(?:no\\.?|number|id)?\\s*#?\\s*|#\\s*)(\\d{1,9})\\b");

    /** The order number in "where is order 12", "order #12", "#12", or null. */
    static Long orderNumber(String msg) {
        Matcher m = ORDER_NUMBER.matcher(msg);
        if (m.find()) return Long.valueOf(m.group(1));
        Matcher t = Pattern.compile("\\btrack\\w*\\s+(?:my\\s+)?(?:order\\s+)?#?(\\d{1,9})\\b").matcher(msg);
        return t.find() ? Long.valueOf(t.group(1)) : null;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
