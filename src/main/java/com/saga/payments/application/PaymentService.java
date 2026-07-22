package com.saga.payments.application;

import com.saga.payments.domain.Payment;
import com.saga.payments.domain.PaymentRejectedException;
import com.saga.payments.domain.PaymentRepository;
import com.saga.payments.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Value("${payments.simulate.reject}")
    private boolean simulateReject;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment charge(String customerId, double amount) {
        if (simulateReject) {
            throw new PaymentRejectedException(customerId);
        }
        return paymentRepository.save(new Payment(null, customerId, amount, PaymentStatus.CHARGED));
    }

    public void refund(Payment payment) {
        paymentRepository.save(payment.withStatus(PaymentStatus.REFUNDED));
    }
}
