package com.saga.shared.events;

public record ShippingGeneratedEvent(Long orderId, Long paymentId, String productId, int quantity, double amount) {
}
