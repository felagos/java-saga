package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyGrantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyGrantJpaRepository extends JpaRepository<LoyaltyGrantEntity, Long> {
}
