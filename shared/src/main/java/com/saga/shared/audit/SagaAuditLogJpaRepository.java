package com.saga.shared.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SagaAuditLogJpaRepository extends JpaRepository<SagaAuditLogEntity, Long> {
}
