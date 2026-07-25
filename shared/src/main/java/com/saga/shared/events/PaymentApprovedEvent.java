package com.saga.shared.events;

public record PaymentApprovedEvent(Long orderId, Long paymentId, String productId, int quantity, double amount) {
}
