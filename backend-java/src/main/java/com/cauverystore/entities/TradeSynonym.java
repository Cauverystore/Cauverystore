package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A word sellers use for goods the tariff calls something else.
 *
 * <h2>What this deliberately is not</h2>
 *
 * It does not map a word to a tax rate. It maps a word to <em>another word</em> - "veshti" to
 * "dhoti" - and the classification is then found the same way it would have been if the seller
 * had typed the tariff's word themselves: by matching the official HSN descriptions, with the
 * rate coming from the CBIC notifications as always.
 *
 * The distinction is the whole point. Asserting "veshti is 5%" would be this marketplace
 * inventing tax law. Asserting "a veshti is what the tariff calls a dhoti" is a translation,
 * which is a thing a Tamil Nadu marketplace can reasonably know and be held to. If the rate on
 * dhotis changes tomorrow, nothing here needs touching.
 *
 * <h2>Why it is needed</h2>
 *
 * The official master names lungi, dhoti, saree and khadi, so those are found by searching for
 * them. It does not contain veshti, mundu, gamcha or angavastram - the words much of this
 * store's catalogue is actually described in. Without a translation the seller searching
 * "veshti" finds nothing, and a seller who finds nothing classifies by guesswork.
 */
@Entity
@Table(name = "trade_synonyms", indexes = {
        @Index(name = "idx_trade_synonym_term", columnList = "term")
})
public class TradeSynonym {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** What the seller types, lower-cased. */
    @Column(name = "term", nullable = false, unique = true)
    private String term;

    /** The word the tariff uses, which must itself appear in the official descriptions. */
    @Column(name = "official_term", nullable = false)
    private String officialTerm;

    /** Why these are the same thing - read by whoever reviews the list later. */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * Who asserted the equivalence. Never blank: an unattributed translation is one nobody can
     * be asked about when it turns out to be wrong.
     */
    @Column(name = "added_by", nullable = false)
    private String addedBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (term != null) term = term.trim().toLowerCase();
        if (officialTerm != null) officialTerm = officialTerm.trim().toLowerCase();
    }

    public TradeSynonym() {
    }

    public TradeSynonym(String term, String officialTerm, String note, String addedBy) {
        this.term = term;
        this.officialTerm = officialTerm;
        this.note = note;
        this.addedBy = addedBy;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public String getOfficialTerm() { return officialTerm; }
    public void setOfficialTerm(String officialTerm) { this.officialTerm = officialTerm; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getAddedBy() { return addedBy; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
