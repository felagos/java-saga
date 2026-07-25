package com.saga.shared.events;

public record ShippingFailedEvent(Long orderId, Long paymentId, String productId, int quantity, String reason) {
}
