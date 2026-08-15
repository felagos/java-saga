package com.saga.checkout.web.dto;

public record OrderResponseDTO(Long id, String customerId, String productId, int quantity, double amount,
        String status) {
}
