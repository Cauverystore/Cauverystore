package com.cauverystore.entities;

import jakarta.persistence.*;

/** UQC (Unit Quantity Code) master from the GST e-invoice portal. */
@Entity
@Table(name = "unit_master")
public class UnitMaster {

    @Id
    @Column(name = "unit_code", length = 10, nullable = false)
    private String unitCode;

    @Column(name = "unit_description", nullable = false)
    private String unitDescription;

    public UnitMaster() {
    }

    public UnitMaster(String unitCode, String unitDescription) {
        this.unitCode = unitCode;
        this.unitDescription = unitDescription;
    }

    public String getUnitCode() { return unitCode; }
    public void setUnitCode(String unitCode) { this.unitCode = unitCode; }

    public String getUnitDescription() { return unitDescription; }
    public void setUnitDescription(String unitDescription) { this.unitDescription = unitDescription; }
}
