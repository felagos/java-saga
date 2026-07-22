package com.saga.shipping.infrastructure.persistence;

import com.saga.shipping.infrastructure.persistence.entity.ShipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShipmentJpaRepository extends JpaRepository<ShipmentEntity, Long> {
}
