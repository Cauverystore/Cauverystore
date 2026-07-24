package com.cauverystore.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "return_requests")
public class ReturnRequest extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;
    private Integer quantity;
    @Column(columnDefinition = "TEXT")
    private String reason;
    private String status;
    private Double refundAmount;
    private String condition;
    private String qualityCheckStatus;
    private Long warehouseReceived;
    private Long replacementOrderId;

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public OrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(OrderItem orderItem) { this.orderItem = orderItem; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public ProductVariant getVariant() { return variant; }
    public void setVariant(ProductVariant variant) { this.variant = variant; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Double refundAmount) { this.refundAmount = refundAmount; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getQualityCheckStatus() { return qualityCheckStatus; }
    public void setQualityCheckStatus(String qualityCheckStatus) { this.qualityCheckStatus = qualityCheckStatus; }
    public Long getWarehouseReceived() { return warehouseReceived; }
    public void setWarehouseReceived(Long warehouseReceived) { this.warehouseReceived = warehouseReceived; }
    public Long getReplacementOrderId() { return replacementOrderId; }
    public void setReplacementOrderId(Long replacementOrderId) { this.replacementOrderId = replacementOrderId; }
}
