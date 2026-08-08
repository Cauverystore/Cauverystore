package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tcs_records")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class TcsRecord {
    public static final String ENTRY_COLLECTION = "COLLECTION";
    public static final String ENTRY_REVERSAL = "REVERSAL";

    // Same displacement as GstInvoice had: constants added between the annotations and the
    // field left @Id sitting on a static String, which Hibernate ignores.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sellerGstin;

    @Column(nullable = false)
    private String marketplace = "NOYYAL";

    private Long orderId;
    private String invoiceNumber;

    private Long sellerId;
    private Long customerId;
    private String customerEmail;
    private String customerGstin;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Column(nullable = false)
    private Double tcsAmount;

    /** The rate actually applied, kept on the row. 0.5% since Notif. 15/2024-CT. */
    private Double tcsRate = 0.5;
    private Double taxableAmount;

    /**
     * Place of supply, as the two-digit state code.
     *
     * GSTR-8 table 3 is keyed on the supplier's GSTIN <em>and</em> the place of supply - one
     * row per supplier per state - so a TCS ledger without it cannot be turned into the return
     * at all. Copied from the invoice rather than derived later, because the invoice already
     * settled the question, including the section 10(1)(b) cases where the answer is not the
     * delivery state.
     */
    @Column(name = "place_of_supply_state", length = 2)
    private String placeOfSupplyState;

    /**
     * Whether the customer was registered.
     *
     * Table 3 asks for supplies to registered and unregistered persons in separate columns, so
     * this has to be recorded per transaction. Derived from the customer's GSTIN when the row is
     * written, not looked up afterwards - a customer who registers next month must not move
     * last month's supplies into a different column of a return already filed.
     */
    @Column(name = "customer_registered")
    private Boolean customerRegistered = Boolean.FALSE;

    /**
     * Whether the supply was inter-state.
     *
     * The utility splits the TCS figure into integrated, central and state tax, and which
     * applies follows the supply, not the seller. Recorded per row because a single supplier can
     * have both kinds in one month.
     */
    @Column(name = "inter_state")
    private Boolean interState = Boolean.FALSE;

    private String filingStatus = "PENDING";
    private String period;
    private LocalDateTime filedAt;

    @Column(columnDefinition = "TEXT")
    /**
     * COLLECTION or REVERSAL.
     *
     * Section 52 charges TCS on NET monthly supplies - gross less returns - so a return has to
     * reduce the month's collection. Without a reversing row the ledger keeps reporting money
     * that was refunded, and GSTR-8 is filed from this table.
     */
    private String entryType = ENTRY_COLLECTION;

    /** The credit note that caused a reversal. Null on a collection. */
    private Long creditNoteId;

    /** The collection row a reversal cancels, so every reversal is traceable to its origin. */
    private Long reversesId;

    private String remarks;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSellerGstin() { return sellerGstin; }
    public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
    public String getMarketplace() { return marketplace; }
    public void setMarketplace(String marketplace) { this.marketplace = marketplace; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public Double getTcsAmount() { return tcsAmount; }
    public void setTcsAmount(Double tcsAmount) { this.tcsAmount = tcsAmount; }
    public Double getTcsRate() { return tcsRate; }
    public void setTcsRate(Double tcsRate) { this.tcsRate = tcsRate; }
    public String getPlaceOfSupplyState() { return placeOfSupplyState; }
    public void setPlaceOfSupplyState(String placeOfSupplyState) { this.placeOfSupplyState = placeOfSupplyState; }
    public Boolean getCustomerRegistered() { return customerRegistered; }
    public void setCustomerRegistered(Boolean customerRegistered) { this.customerRegistered = customerRegistered; }

    /** True when the buyer held a GSTIN at the time of supply. */
    @jakarta.persistence.Transient
    public boolean isCustomerRegistered() { return Boolean.TRUE.equals(customerRegistered); }

    public Boolean getInterState() { return interState; }
    public void setInterState(Boolean interState) { this.interState = interState; }

    @jakarta.persistence.Transient
    public boolean isInterStateSupply() { return Boolean.TRUE.equals(interState); }

    public Double getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(Double taxableAmount) { this.taxableAmount = taxableAmount; }
    public String getFilingStatus() { return filingStatus; }
    public void setFilingStatus(String filingStatus) { this.filingStatus = filingStatus; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public LocalDateTime getFiledAt() { return filedAt; }
    public void setFiledAt(LocalDateTime filedAt) { this.filedAt = filedAt; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public Long getCreditNoteId() { return creditNoteId; }
    public void setCreditNoteId(Long creditNoteId) { this.creditNoteId = creditNoteId; }
    public Long getReversesId() { return reversesId; }
    public void setReversesId(Long reversesId) { this.reversesId = reversesId; }

    /** True when this row cancels an earlier collection rather than recording one. */
    @jakarta.persistence.Transient
    public boolean isReversal() { return ENTRY_REVERSAL.equals(entryType); }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
