package com.cauverystore.entities;

import jakarta.persistence.*;

@Entity
@Table(name = "supplier_products")
public class SupplierProduct extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    private Double unitPrice;
    private Integer leadTimeDays;
    private String sku;

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Double unitPrice) { this.unitPrice = unitPrice; }
    public Integer getLeadTimeDays() { return leadTimeDays; }
    public void setLeadTimeDays(Integer leadTimeDays) { this.leadTimeDays = leadTimeDays; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
}
