package com.saga.shared.events;

public record PaymentRejectedEvent(Long orderId, Long paymentId, String productId, int quantity) {
}
