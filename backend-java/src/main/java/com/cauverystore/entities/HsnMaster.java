package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * HSN/SAC classification codes as published by GSTN on the e-invoice portal
 * (einvoice.gst.gov.in/master-codes). Classification only - this carries no tax
 * rate, because GSTN publishes codes while CBIC publishes rates. See GstRateMaster.
 */
@Entity
@Table(name = "hsn_master", indexes = {
        @Index(name = "idx_hsn_master_chapter", columnList = "chapter")
})
public class HsnMaster {

    @Id
    @Column(name = "hsn_code", length = 8, nullable = false)
    private String hsnCode;

    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    /** First two digits of the code - the HS chapter (01-99). */
    @Column(name = "chapter", length = 2)
    private String chapter;

    /** 4, 6 or 8 - the classification level, which decides who may use it (turnover-based). */
    @Column(name = "digits")
    private Integer digits;

    public HsnMaster() {
    }

    public HsnMaster(String hsnCode, String description) {
        this.hsnCode = hsnCode;
        this.description = description;
        this.chapter = hsnCode != null && hsnCode.length() >= 2 ? hsnCode.substring(0, 2) : null;
        this.digits = hsnCode != null ? hsnCode.length() : null;
    }

    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getChapter() { return chapter; }
    public void setChapter(String chapter) { this.chapter = chapter; }

    public Integer getDigits() { return digits; }
    public void setDigits(Integer digits) { this.digits = digits; }
}
