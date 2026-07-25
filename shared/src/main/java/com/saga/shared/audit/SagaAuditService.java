package com.saga.shared.audit;

import org.springframework.stereotype.Component;

@Component
public class SagaAuditService {

    private final SagaAuditLogJpaRepository repository;

    public SagaAuditService(SagaAuditLogJpaRepository repository) {
        this.repository = repository;
    }

    public void record(Long orderId, String step, String outcome, String detail) {
        repository.save(new SagaAuditLogEntity(orderId, step, outcome, detail));
    }
}
