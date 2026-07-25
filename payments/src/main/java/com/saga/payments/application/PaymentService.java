package com.saga.payments.application;

import com.saga.payments.domain.Payment;
import com.saga.payments.domain.PaymentRepository;
import com.saga.payments.domain.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    public Payment initiate(Long orderId, String customerId, double amount) {
        return paymentRepository.save(new Payment(null, orderId, customerId, amount, PaymentStatus.PENDING));
    }

    public Payment confirm(Payment payment) {
        return paymentRepository.save(payment.withStatus(PaymentStatus.CHARGED));
    }

    public Payment reject(Payment payment) {
        return paymentRepository.save(payment.withStatus(PaymentStatus.REJECTED));
    }

    public void refund(Payment payment) {
        paymentRepository.save(payment.withStatus(PaymentStatus.REFUNDED));
    }

    public Payment findById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
