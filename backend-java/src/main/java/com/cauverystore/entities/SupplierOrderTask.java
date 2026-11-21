package com.cauverystore.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One customer order line that a person needs to buy from the supplier. The store prepares it; a
 * person checks it and pays on the supplier's site. The delivery address is read from the order
 * when shown and is deliberately not copied here.
 */
@Entity
@Table(name = "supplier_order_tasks",
        uniqueConstraints = @UniqueConstraint(name = "uk_supplier_order_task_item", columnNames = "order_item_id"),
        indexes = @Index(name = "idx_supplier_order_task_status", columnList = "status"))
@Getter
@Setter
public class SupplierOrderTask extends BaseEntity {

    public static final String TO_ORDER = "TO_ORDER";
    public static final String ORDERED = "ORDERED";
    public static final String CANCELLED = "CANCELLED";
    /** Bought from the supplier, then the customer's order was cancelled: a person must sort it out. */
    public static final String PROBLEM = "PROBLEM";

    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "order_item_id", nullable = false)
    private Long orderItemId;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", length = 255)
    private String productName;
    @Column(length = 60)
    private String sku;
    @Column(name = "supplier_url", length = 500)
    private String supplierUrl;
    @Column(nullable = false)
    private int quantity;
    @Column(name = "sale_price")
    private Double salePrice;

    @Column(nullable = false, length = 20)
    private String status = TO_ORDER;

    @Column(name = "supplier_order_ref", length = 80)
    private String supplierOrderRef;
    @Column(name = "amount_paid")
    private Double amountPaid;
    @Column(name = "ordered_by", length = 120)
    private String orderedBy;
    @Column(name = "ordered_at")
    private LocalDateTime orderedAt;
}
