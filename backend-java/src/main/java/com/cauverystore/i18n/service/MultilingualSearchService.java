package com.cauverystore.i18n.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Search that works across languages.
 *
 *  1. The query is split into words. Each word becomes a GROUP: the word itself plus every synonym and
 *     transliteration of it from search_synonyms (e.g. "பருத்தி" -> {பருத்தி, cotton, paruthi, कॉटन, ...}).
 *  2. Tier 1 - "all-words": a product must contain at least one term from EVERY group.
 *  3. Tier 2 - "any-word": if tier 1 finds nothing, a product matching ANY group is returned.
 *  4. Tier 3 - "contains": if still nothing, a plain case-insensitive substring match (handles partial typing).
 *
 * Known limits (phase 1): no spelling-mistake correction, and no handling of Indian-language word endings
 * (e.g. "பருத்தியால்"). Both are possible later (OpenSearch / a stemmer) - see the architecture doc.
 */
@Service
public class MultilingualSearchService {

    private static final Pattern SPLIT = Pattern.compile("[\\s\\p{Punct}।॥]+");
    private static final String VISIBLE =
            "p.active = true and (p.approval_status is null or lower(p.approval_status) = 'approved')";

    private final JdbcTemplate jdbc;
    public MultilingualSearchService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Result(List<Long> ids, String matchType) {}

    public Result search(String rawQuery, String lang, int limit) {
        String q = Normalizer.normalize(rawQuery == null ? "" : rawQuery, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT).strip();
        if (q.length() > 100) q = q.substring(0, 100);
        if (q.isEmpty()) return new Result(List.of(), "none");

        List<String> tokens = Arrays.stream(SPLIT.split(q)).filter(s -> !s.isBlank()).limit(8).toList();
        if (tokens.isEmpty()) return new Result(List.of(), "none");

        List<List<String>> groups = new ArrayList<>();
        for (String t : tokens) groups.add(expand(t));

        List<Long> ids = tokenQuery(groups, tokens, lang, limit, true);
        if (!ids.isEmpty()) return new Result(ids, "all-words");

        if (groups.size() > 1) {
            ids = tokenQuery(groups, tokens, lang, limit, false);
            if (!ids.isEmpty()) return new Result(ids, "any-word");
        }

        String like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        ids = jdbc.queryForList(
                "select p.id from product_search_index i join products p on p.id = i.product_id " +
                "where " + VISIBLE + " and i.all_text ilike ? order by p.id limit ?", Long.class, like, limit);
        return new Result(ids, ids.isEmpty() ? "none" : "contains");
    }

    /** The word itself plus all synonyms/transliterations of it, split into single words. */
    List<String> expand(String token) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(token);
        jdbc.query("""
            select s2.term, s2.roman_term from search_synonyms s1
              join search_synonyms s2 on s2.concept_key = s1.concept_key
             where lower(s1.term) = ? or lower(s1.roman_term) = ?
            """, rs -> {
                addWords(out, rs.getString(1));
                addWords(out, rs.getString(2));
            }, token, token);
        return new ArrayList<>(out);
    }

    private static void addWords(Set<String> out, String phrase) {
        if (phrase == null) return;
        for (String w : SPLIT.split(Normalizer.normalize(phrase, Normalizer.Form.NFC).toLowerCase(Locale.ROOT))) {
            if (!w.isBlank()) out.add(w);
        }
    }

    private List<Long> tokenQuery(List<List<String>> groups, List<String> originals, String lang, int limit, boolean all) {
        StringBuilder where = new StringBuilder();
        for (int i = 0; i < groups.size(); i++) {
            if (i > 0) where.append(all ? " and " : " or ");
            where.append("i.tokens && ?");
        }
        String sql = """
            select p.id
              from product_search_index i
              join products p on p.id = i.product_id
             where %s and (%s)
             order by (select count(*) from unnest(?) o where o = any(i.tokens)) desc,
                      (select count(*) from product_translations t
                        where t.product_id = p.id and t.language_code = ? and t.status = 'PUBLISHED') desc,
                      p.id
             limit ?
            """.formatted(VISIBLE, where);

        return jdbc.query(con -> {
            var ps = con.prepareStatement(sql);
            int n = 1;
            for (List<String> g : groups) {
                Array arr = con.createArrayOf("text", g.toArray());
                ps.setArray(n++, arr);
            }
            ps.setArray(n++, con.createArrayOf("text", originals.toArray()));
            ps.setString(n++, lang);
            ps.setInt(n, limit);
            return ps;
        }, (rs, i) -> rs.getLong(1));
    }
}
