package com.saga.shared.audit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "saga_audit_log")
public class SagaAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String step;
    private String outcome;
    private String detail;
    private Instant createdAt;

    protected SagaAuditLogEntity() {
    }

    public SagaAuditLogEntity(Long orderId, String step, String outcome, String detail) {
        this.orderId = orderId;
        this.step = step;
        this.outcome = outcome;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
