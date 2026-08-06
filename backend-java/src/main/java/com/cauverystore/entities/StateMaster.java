package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * GST state codes. The first two digits of a GSTIN are the state code, which is how
 * intra-state (CGST+SGST) vs inter-state (IGST) supply is determined.
 */
@Entity
@Table(name = "state_master")
public class StateMaster {

    @Id
    @Column(name = "state_code", length = 2, nullable = false)
    private String stateCode;

    @Column(name = "state_name", nullable = false)
    private String stateName;

    public StateMaster() {
    }

    public StateMaster(String stateCode, String stateName) {
        this.stateCode = stateCode;
        this.stateName = stateName;
    }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
}
