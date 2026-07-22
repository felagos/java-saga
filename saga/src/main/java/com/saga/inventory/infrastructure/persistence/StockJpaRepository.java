package com.saga.inventory.infrastructure.persistence;

import com.saga.inventory.infrastructure.persistence.entity.StockEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockJpaRepository extends JpaRepository<StockEntity, String> {
}
