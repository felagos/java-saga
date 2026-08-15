package com.saga.checkout.web.dto;

public class OrderResponseDTO {
    private final Long id;
    private final String customerId;
    private final String productId;
    private final int quantity;
    private final double amount;
    private final String status;

    public OrderResponseDTO() {
        this(null, null, null, 0, 0, null);
    }

    public OrderResponseDTO(Long id, String customerId, String productId, int quantity, double amount, String status) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
    }

    public Long id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String productId() {
        return productId;
    }

    public int quantity() {
        return quantity;
    }

    public double amount() {
        return amount;
    }

    public String status() {
        return status;
    }
}
