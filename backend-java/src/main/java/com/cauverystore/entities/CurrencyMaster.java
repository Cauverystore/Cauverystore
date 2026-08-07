package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * ISO currency codes the e-invoice portal accepts.
 *
 * An export invoice states the currency it was raised in. Domestic supplies are always INR, so
 * this only matters once the store sells abroad - but a rejected e-invoice at that point is a
 * blocked shipment, not a warning.
 */
@Entity
@Table(name = "currency_master")
public class CurrencyMaster {

    @Id
    @Column(name = "currency_code", length = 3, nullable = false)
    private String currencyCode;

    @Column(name = "currency_name", nullable = false)
    private String currencyName;

    public CurrencyMaster() {
    }

    public CurrencyMaster(String currencyCode, String currencyName) {
        this.currencyCode = currencyCode;
        this.currencyName = currencyName;
    }

    public String getCurrencyCode() { return currencyCode; }
    public void setCurrencyCode(String currencyCode) { this.currencyCode = currencyCode; }
    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }
}
