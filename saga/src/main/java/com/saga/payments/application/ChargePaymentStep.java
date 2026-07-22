package com.saga.payments.application;

import com.saga.bff.orchestrator.SagaStep;
import com.saga.payments.domain.Payment;

/** Per-checkout adapter, not a Spring bean: carries the created Payment for compensate(). */
public class ChargePaymentStep implements SagaStep {

    private final PaymentService paymentService;
    private final String customerId;
    private final double amount;

    private Payment payment;

    public ChargePaymentStep(PaymentService paymentService, String customerId, double amount) {
        this.paymentService = paymentService;
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public void execute() {
        payment = paymentService.charge(customerId, amount);
    }

    @Override
    public void compensate() {
        paymentService.refund(payment);
    }
}
