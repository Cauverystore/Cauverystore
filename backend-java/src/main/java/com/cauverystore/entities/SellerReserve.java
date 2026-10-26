package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Money held back from a seller's payouts against returns and disputes. {@code percentBp} is the
 * share of each payout withheld, in basis points (500 = 5 per cent); zero means no reserve.
 * {@code heldPaise} is what is held now. Releasing it posts a ledger line, so it flows back out
 * through the next settlement run like any other balance.
 */
@Entity
@Table(name = "seller_reserves")
public class SellerReserve {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "percent_bp", nullable = false)
    private int percentBp;

    @Column(name = "held_paise", nullable = false)
    private long heldPaise;

    @Column(name = "updated_by", length = 120)
    private String updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = LocalDateTime.now(); }

    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public int getPercentBp() { return percentBp; }
    public void setPercentBp(int percentBp) { this.percentBp = percentBp; }
    public long getHeldPaise() { return heldPaise; }
    public void setHeldPaise(long heldPaise) { this.heldPaise = heldPaise; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
