package com.cauverystore.entities;

import jakarta.persistence.*;

/**
 * Which pincode prefixes belong to which state.
 *
 * A state can appear more than once - Puducherry has four separate ranges because its districts
 * are scattered, and Daman and Diu has two - so the key is generated rather than the state code.
 *
 * The point of holding this is that place of supply must be right and a typed state name can be
 * wrong. The pincode is on the same address and is far harder to get wrong, so it can both
 * recover a missing state and contradict a mistyped one.
 */
@Entity
@Table(name = "pincode_state_ranges", indexes = {
        @Index(name = "idx_pincode_prefix", columnList = "prefix_from,prefix_to")
})
public class PincodeStateRange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_code", length = 2, nullable = false)
    private String stateCode;

    @Column(name = "state_name", nullable = false)
    private String stateName;

    /** First three digits of the pincode, inclusive. */
    @Column(name = "prefix_from", nullable = false)
    private Integer prefixFrom;

    @Column(name = "prefix_to", nullable = false)
    private Integer prefixTo;

    public PincodeStateRange() {
    }

    public PincodeStateRange(String stateCode, String stateName, Integer prefixFrom, Integer prefixTo) {
        this.stateCode = stateCode;
        this.stateName = stateName;
        this.prefixFrom = prefixFrom;
        this.prefixTo = prefixTo;
    }

    /** Whether a full six-digit pincode falls in this range. */
    @Transient
    public boolean covers(String pincode) {
        if (pincode == null) return false;
        String digits = pincode.replaceAll("\\D", "");
        if (digits.length() < 3) return false;
        int prefix = Integer.parseInt(digits.substring(0, 3));
        return prefix >= prefixFrom && prefix <= prefixTo;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getStateName() { return stateName; }
    public void setStateName(String stateName) { this.stateName = stateName; }
    public Integer getPrefixFrom() { return prefixFrom; }
    public void setPrefixFrom(Integer prefixFrom) { this.prefixFrom = prefixFrom; }
    public Integer getPrefixTo() { return prefixTo; }
    public void setPrefixTo(Integer prefixTo) { this.prefixTo = prefixTo; }
}
