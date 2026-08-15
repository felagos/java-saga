package com.saga.checkout.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public class CheckoutRequestDTO {
    @NotBlank
    private final String customerId;
    @NotBlank
    private final String productId;
    @Positive
    private final int quantity;
    @Positive
    private final double amount;

    public CheckoutRequestDTO() {
        this(null, null, 0, 0);
    }

    public CheckoutRequestDTO(String customerId, String productId, int quantity, double amount) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
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
}
