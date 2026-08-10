package com.cauverystore.entities;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "refunds")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate refundDate;

    private Double amount;

    private Long orderId;

    private String reason;

    private String status;

    // The gateway's own refund reference (e.g. Razorpay's rfnd_... id) - null for orders that
    // were never actually charged online (COD) or where the gateway call itself failed.
    private String gatewayRefundId;

    /**
     * The return this refund settles, when it came from one rather than from a cancellation.
     *
     * Held so a second attempt can find the first. Without it there is no way to ask "has this
     * return already been refunded", and the only thing standing between a customer and a second
     * payout is nobody pressing the button twice.
     */
    private Long returnRequestId;

    /**
     * How the money is going back.
     *
     * ORIGINAL_METHOD means the gateway sent it to the card, UPI handle or account that paid -
     * which is the only route Razorpay offers, and the only one that needs no bank details from
     * the customer. MANUAL_BANK_TRANSFER is what is left for an order paid in cash on delivery:
     * there is no payment to reverse, so somebody has to pay it out and record that they did.
     */
    private String refundMethod;

    /** Razorpay's own wording: "instant" where the rail supports it, otherwise "normal". */
    private String speed;

    /** What the customer should be told to expect, in plain words. */
    private String expectedCredit;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getRefundDate() {
        return refundDate;
    }

    public void setRefundDate(LocalDate refundDate) {
        this.refundDate = refundDate;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGatewayRefundId() {
        return gatewayRefundId;
    }

    public Long getReturnRequestId() { return returnRequestId; }
    public void setReturnRequestId(Long returnRequestId) { this.returnRequestId = returnRequestId; }
    public String getRefundMethod() { return refundMethod; }
    public void setRefundMethod(String refundMethod) { this.refundMethod = refundMethod; }
    public String getSpeed() { return speed; }
    public void setSpeed(String speed) { this.speed = speed; }
    public String getExpectedCredit() { return expectedCredit; }
    public void setExpectedCredit(String expectedCredit) { this.expectedCredit = expectedCredit; }

    public void setGatewayRefundId(String gatewayRefundId) {
        this.gatewayRefundId = gatewayRefundId;
    }
}
