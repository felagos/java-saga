package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.domain.LoyaltyGrant;
import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyGrantEntity;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyGrantPersistenceMapper {

    public LoyaltyGrant toDomain(LoyaltyGrantEntity entity) {
        return new LoyaltyGrant(entity.getId(), entity.getCustomerId(), entity.getPoints(), entity.isReverted());
    }

    public LoyaltyGrantEntity toEntity(LoyaltyGrant grant) {
        return new LoyaltyGrantEntity(grant.customerId(), grant.points(), grant.reverted());
    }
}
