package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoyaltyAccountJpaRepository extends JpaRepository<LoyaltyAccountEntity, String> {
}
