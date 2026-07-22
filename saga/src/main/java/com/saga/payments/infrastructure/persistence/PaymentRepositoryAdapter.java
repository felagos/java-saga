package com.saga.payments.infrastructure.persistence;

import com.saga.payments.domain.Payment;
import com.saga.payments.domain.PaymentRepository;
import com.saga.payments.infrastructure.persistence.entity.PaymentEntity;
import org.springframework.stereotype.Component;

@Component
public class PaymentRepositoryAdapter implements PaymentRepository {

    private final PaymentJpaRepository jpaRepository;
    private final PaymentPersistenceMapper mapper;

    public PaymentRepositoryAdapter(PaymentJpaRepository jpaRepository, PaymentPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Payment save(Payment payment) {
        if (payment.id() == null) {
            PaymentEntity saved = jpaRepository.save(mapper.toEntity(payment));
            return mapper.toDomain(saved);
        }
        PaymentEntity entity = jpaRepository.findById(payment.id())
                .orElseThrow(() -> new IllegalStateException("Payment not found: " + payment.id()));
        entity.setStatus(payment.status());
        return mapper.toDomain(entity);
    }
}
