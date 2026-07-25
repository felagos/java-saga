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
        if (payment.status() == PaymentStatus.CHARGED) {
            return payment;
        }
        if (payment.status() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot charge payment " + payment.id() + " from status " + payment.status());
        }
        return paymentRepository.save(payment.withStatus(PaymentStatus.CHARGED));
    }

    public Payment reject(Payment payment) {
        if (payment.status() == PaymentStatus.REJECTED) {
            return payment;
        }
        if (payment.status() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot reject payment " + payment.id() + " from status " + payment.status());
        }
        return paymentRepository.save(payment.withStatus(PaymentStatus.REJECTED));
    }

    public void refund(Payment payment) {
        if (payment.status() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.status() != PaymentStatus.CHARGED) {
            throw new IllegalStateException("Cannot refund payment " + payment.id() + " from status " + payment.status());
        }
        paymentRepository.save(payment.withStatus(PaymentStatus.REFUNDED));
    }

    public Payment findById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }
}
