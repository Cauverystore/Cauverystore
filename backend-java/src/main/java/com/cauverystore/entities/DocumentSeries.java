package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * One numbering series for one supplier's GST documents (invoices, credit notes or debit notes)
 * in one month.
 *
 * Rule 46(b) and Rule 53 of the CGST Rules: each supplier numbers its documents consecutively,
 * unique within the financial year. On the marketplace the seller is the supplier, so every
 * seller GSTIN needs its own series. The number is prefix + yyMM + "/" + 5 digits; the month in
 * the number keeps it unique across the year, and a prefix is never shared between GSTINs, so
 * the number is also unique across the whole store.
 *
 * The row is locked while the next number is taken, inside the transaction that saves the
 * document. Two orders can't take the same number, and a failed save rolls the counter back
 * instead of leaving a gap.
 */
@Entity
@Table(name = "gst_document_series",
        uniqueConstraints = @UniqueConstraint(name = "uk_doc_series",
                columnNames = {"doc_type", "prefix", "period"}),
        indexes = @Index(name = "idx_doc_series_gstin", columnList = "gstin, doc_type"))
public class DocumentSeries {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String gstin;

    /** INV, CN or DN. */
    @Column(name = "doc_type", nullable = false, length = 3)
    private String docType;

    @Column(nullable = false, length = 6)
    private String prefix;

    /** yyMM. */
    @Column(nullable = false, length = 4)
    private String period;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;

    public DocumentSeries() { }

    public DocumentSeries(String gstin, String docType, String prefix, String period, int lastNumber) {
        this.gstin = gstin;
        this.docType = docType;
        this.prefix = prefix;
        this.period = period;
        this.lastNumber = lastNumber;
    }

    public Long getId() { return id; }
    public String getGstin() { return gstin; }
    public String getDocType() { return docType; }
    public String getPrefix() { return prefix; }
    public String getPeriod() { return period; }
    public int getLastNumber() { return lastNumber; }
    public void setLastNumber(int lastNumber) { this.lastNumber = lastNumber; }
}
