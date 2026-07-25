package com.saga.shared.events;

public record PaymentInitiatedEvent(Long orderId, Long paymentId, String customerId, String productId,
                                     int quantity, double amount) {
}
