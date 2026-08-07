package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * An HS chapter - the two-digit top of the classification tree.
 *
 * <h2>Why this is a table and not two digits of a code</h2>
 *
 * CBIC prices some goods against a whole chapter rather than a heading, so 36 chapter codes sit
 * in the rate seed. They match nothing in {@link HsnMaster}, which holds only 4-, 6- and
 * 8-digit rows because an invoice never carries a 2-digit HSN. Both are right - they are
 * different levels of one tree, and GstRateResolver bridges them by walking 8 -> 6 -> 4 -> 2.
 *
 * What was missing was a name. A chapter with no row can only be shown to a seller as "61", and
 * a check that compares codes for equality reports 36 orphans that are not orphans. This gives
 * the level a row of its own so it can be named, browsed and matched by lookup.
 *
 * <h2>Why a title may be null</h2>
 *
 * Five chapters carry no title anywhere in the published master. A null says so. The
 * alternative - writing in the five titles from general knowledge - would put unsourced text
 * into the data that decides what tax is charged, and every rate in this system is traceable to
 * a published document by design. Every chapter the rate seed actually prices has a title, so
 * nothing depends on the gap.
 */
@Entity
@Table(name = "chapter_master")
public class ChapterMaster {

    /** Two digits, "01" to "99". Chapter 77 is reserved in the tariff and has no row. */
    @Id
    @Column(name = "chapter", length = 2, nullable = false)
    private String chapter;

    /** The Customs Tariff chapter title, or null where the master publishes none. */
    @Column(name = "title", columnDefinition = "TEXT")
    private String title;

    @Column(name = "title_source")
    private String titleSource;

    public ChapterMaster() {
    }

    public ChapterMaster(String chapter, String title, String titleSource) {
        this.chapter = chapter;
        this.title = title;
        this.titleSource = titleSource;
    }

    /** True when this chapter can be shown to a seller by name rather than by number alone. */
    public boolean isNamed() {
        return title != null && !title.isBlank();
    }

    /** "61 - ARTICLES OF APPAREL...", falling back to the bare number when unnamed. */
    public String getLabel() {
        return isNamed() ? chapter + " - " + title : chapter;
    }

    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleSource() { return titleSource; }
    public void setTitleSource(String titleSource) { this.titleSource = titleSource; }
}
