package com.saga.payments.domain;

public record Payment(Long id, String customerId, double amount, PaymentStatus status) {

    public Payment withStatus(PaymentStatus newStatus) {
        return new Payment(id, customerId, amount, newStatus);
    }
}
