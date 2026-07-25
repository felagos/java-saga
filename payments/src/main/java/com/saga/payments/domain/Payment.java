package com.saga.payments.domain;

public record Payment(Long id, Long orderId, String customerId, double amount, PaymentStatus status) {

    public Payment withStatus(PaymentStatus newStatus) {
        return new Payment(id, orderId, customerId, amount, newStatus);
    }
}
