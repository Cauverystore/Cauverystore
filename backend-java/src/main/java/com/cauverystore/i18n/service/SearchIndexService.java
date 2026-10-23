package com.cauverystore.i18n.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps product_search_index up to date. For each active product it stores one blob of text containing the
 * original name/description, brand, category, every PUBLISHED translation, and the other-language synonyms of
 * any known word in the name/category. That blob is why a Tamil search can find an English-only product.
 *
 * A full rebuild runs every 30 minutes (cheap for a catalogue of thousands) and after any translation change.
 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    private static final String SPLIT_REGEX = "[\\s[:punct:]।॥]+";

    private static final String INDEX_SQL = """
        with base as (
          select p.id,
                 concat_ws(' ', p.name, p.short_description, c.name) as core_text,
                 concat_ws(' ', p.name, p.short_description, left(coalesce(p.description,''), 3000),
                           p.brand, c.name, b.name) as orig_text,
                 p.category_id
            from products p
            left join categories c on c.id = p.category_id
            left join brands b on b.id = p.brand_id
           where p.active = true %s
        ),
        full_text as (
          select base.id,
                 concat_ws(' ',
                   base.orig_text,
                   (select string_agg(concat_ws(' ', t.name, t.short_description), ' ')
                      from product_translations t where t.product_id = base.id and t.status = 'PUBLISHED'),
                   (select string_agg(ct.name, ' ')
                      from category_translations ct where ct.category_id = base.category_id and ct.status = 'PUBLISHED'),
                   (select string_agg(distinct concat_ws(' ', s2.term, s2.roman_term), ' ')
                      from search_synonyms s1
                      join search_synonyms s2 on s2.concept_key = s1.concept_key
                     where (s1.term !~ '\\s' and lower(s1.term) = any(regexp_split_to_array(lower(base.core_text), '%s')))
                        or (s1.term ~ '\\s' and position(lower(s1.term) in lower(base.core_text)) > 0))
                 ) as txt
            from base
        )
        insert into product_search_index (product_id, all_text, tokens, updated_at)
        select id, normalize(txt, NFC), regexp_split_to_array(lower(normalize(txt, NFC)), '%s'), now()
          from full_text
        on conflict (product_id) do update
           set all_text = excluded.all_text, tokens = excluded.tokens, updated_at = now()
        """;

    private final JdbcTemplate jdbc;
    public SearchIndexService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public int reindexAll() {
        int n = jdbc.update(INDEX_SQL.formatted("", SPLIT_REGEX, SPLIT_REGEX));
        // drop index rows of products that are no longer active
        jdbc.update("delete from product_search_index i using products p where p.id = i.product_id and p.active = false");
        return n;
    }

    public void reindexProduct(long productId) {
        jdbc.update(INDEX_SQL.formatted("and p.id = " + productId, SPLIT_REGEX, SPLIT_REGEX));
    }

    @Scheduled(initialDelay = 120_000, fixedDelay = 1_800_000)
    public void scheduledRebuild() {
        try {
            reindexAll();
        } catch (Exception e) {
            // Most likely: the V5 SQL has not been run yet. Never let this stop the app.
            log.warn("Search index rebuild skipped: {}", e.getMessage());
        }
    }
}
