package com.saga.payments.domain;

public interface PaymentRepository {

    Payment save(Payment payment);

    Payment findById(Long id);
}
