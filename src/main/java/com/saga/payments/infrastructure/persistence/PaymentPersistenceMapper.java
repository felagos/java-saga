package com.saga.payments.infrastructure.persistence;

import com.saga.payments.domain.Payment;
import com.saga.payments.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentPersistenceMapper {

    public Payment toDomain(PaymentEntity entity) {
        return new Payment(entity.getId(), entity.getCustomerId(), entity.getAmount(), entity.getStatus());
    }

    public PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(payment.customerId(), payment.amount(), payment.status());
    }
}
