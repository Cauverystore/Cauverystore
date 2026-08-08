package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * A Services Accounting Code and the rate published for it.
 *
 * <h2>Why services need their own table</h2>
 *
 * The e-invoice HSN master carries 600 SAC codes but no rates, so until now a service had a
 * code and no way to be taxed. The marketplace's own commission and the delivery line both got
 * 18% written into the code that raises them, which is a rate nobody can trace to a
 * notification and nobody would find if it changed.
 *
 * <h2>Why it is not folded into GstRateMaster</h2>
 *
 * Goods rates are value-banded, packaging-split, and read from chapter down to tariff item.
 * None of that applies to services: a SAC has one rate, and the resolver's whole 8-6-4-2 walk
 * is meaningless against a code that is not a hierarchy of goods. Sharing the table would mean
 * every goods lookup stepping over service rows and every service lookup carrying conditions
 * that can never be true.
 *
 * Effective-dated for the same reason goods rates are: an invoice raised last year must still
 * resolve to the rate that applied last year.
 */
@Entity
@Table(name = "sac_master", indexes = {
        @Index(name = "idx_sac_master_effective", columnList = "sac_code, effective_from")
})
public class SacMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** e.g. "996511" for road transport of goods, "998599" for support services. */
    @Column(name = "sac_code", length = 10, nullable = false)
    private String sacCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "gst_rate", nullable = false)
    private Double gstRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** Null while the rate is still in force. */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** The notification this came from. A rate without one cannot be defended. */
    @Column(columnDefinition = "TEXT")
    private String source;

    /**
     * VERIFIED or UNVERIFIED, mirroring GstRateMaster.
     *
     * A service rate that nobody has checked is not charged, for the same reason an unchecked
     * goods rate is not: guessing produces an invoice that looks right and is wrong.
     */
    @Column(nullable = false)
    private String status = "UNVERIFIED";

    @Column(columnDefinition = "TEXT")
    private String notes;

    public SacMaster() {
    }

    public SacMaster(String sacCode, String description, Double gstRate,
                     LocalDate effectiveFrom, String source, String status) {
        this.sacCode = sacCode;
        this.description = description;
        this.gstRate = gstRate;
        this.effectiveFrom = effectiveFrom;
        this.source = source;
        this.status = status;
    }

    @Transient
    public boolean isVerified() { return "VERIFIED".equals(status); }

    /** Whether this row is the one in force on a given date. */
    @Transient
    public boolean appliesOn(LocalDate date) {
        if (date == null) return false;
        if (effectiveFrom != null && effectiveFrom.isAfter(date)) return false;
        return effectiveTo == null || !effectiveTo.isBefore(date);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSacCode() { return sacCode; }
    public void setSacCode(String sacCode) { this.sacCode = sacCode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getGstRate() { return gstRate; }
    public void setGstRate(Double gstRate) { this.gstRate = gstRate; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public void setEffectiveTo(LocalDate effectiveTo) { this.effectiveTo = effectiveTo; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
