package com.cauverystore.exception;

public class InvalidOrderStatusException extends RuntimeException {

    public InvalidOrderStatusException(String status) {
        super(status == null || status.isBlank()
                ? "Order status is required"
                : "Invalid order status: " + status + ". Allowed values: PLACED, CONFIRMED, PACKED, SHIPPED, DELIVERED, CANCELLED, REFUNDED.");
    }
}
