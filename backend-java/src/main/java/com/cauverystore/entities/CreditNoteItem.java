package com.cauverystore.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

@Entity
@Table(name = "credit_note_items")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CreditNoteItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "credit_note_id", nullable = false)
    @JsonIgnoreProperties("items")
    private CreditNote creditNote;

    private String productName;
    private String hsnCode;
    private String sacCode;
    private String unitOfMeasure = "NOS";
    private Integer quantity;
    private Double unitPrice;
    private Double taxableValue;
    private Double cgstRate;
    private Double cgstAmount;
    private Double sgstRate;
    private Double sgstAmount;
    private Double igstRate;
    private Double igstAmount;
    private Double totalAmount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public CreditNote getCreditNote() { return creditNote; }
    public void setCreditNote(CreditNote creditNote) { this.creditNote = creditNote; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getHsnCode() { return hsnCode; }
    public void setHsnCode(String hsnCode) { this.hsnCode = hsnCode; }
    public String getSacCode() { return sacCode; }
    public void setSacCode(String sacCode) { this.sacCode = sacCode; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public Double getTaxableValue() { return taxableValue; }
    public void setTaxableValue(Double taxableValue) { this.taxableValue = taxableValue; }
    public Double getCgstRate() { return cgstRate; }
    public void setCgstRate(Double cgstRate) { this.cgstRate = cgstRate; }
    public Double getCgstAmount() { return cgstAmount; }
    public void setCgstAmount(Double cgstAmount) { this.cgstAmount = cgstAmount; }
    public Double getSgstRate() { return sgstRate; }
    public void setSgstRate(Double sgstRate) { this.sgstRate = sgstRate; }
    public Double getSgstAmount() { return sgstAmount; }
    public void setSgstAmount(Double sgstAmount) { this.sgstAmount = sgstAmount; }
    public Double getIgstRate() { return igstRate; }
    public void setIgstRate(Double igstRate) { this.igstRate = igstRate; }
    public Double getIgstAmount() { return igstAmount; }
    public void setIgstAmount(Double igstAmount) { this.igstAmount = igstAmount; }
    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
}
