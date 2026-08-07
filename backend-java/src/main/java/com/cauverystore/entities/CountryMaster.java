package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * ISO country codes as the e-invoice portal accepts them.
 *
 * Needed on an export invoice, where the place of supply is a country rather than a state. The
 * IRP validates the code against its own list, so a country it does not carry gets the invoice
 * rejected outright - which is why this is held locally rather than assumed from an ISO library.
 */
@Entity
@Table(name = "country_master")
public class CountryMaster {

    @Id
    @Column(name = "country_code", length = 2, nullable = false)
    private String countryCode;

    @Column(name = "country_name", nullable = false)
    private String countryName;

    public CountryMaster() {
    }

    public CountryMaster(String countryCode, String countryName) {
        this.countryCode = countryCode;
        this.countryName = countryName;
    }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    public String getCountryName() { return countryName; }
    public void setCountryName(String countryName) { this.countryName = countryName; }
}
