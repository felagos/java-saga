package com.saga.checkout.web;

public record OrderResponse(Long id, String customerId, String productId, int quantity, double amount,
        String status) {
}
