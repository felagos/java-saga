package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.domain.LoyaltyAccount;
import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyAccountEntity;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyAccountPersistenceMapper {

    public LoyaltyAccount toDomain(LoyaltyAccountEntity entity) {
        return new LoyaltyAccount(entity.getCustomerId(), entity.getPoints());
    }

    public LoyaltyAccountEntity toEntity(LoyaltyAccount account) {
        return new LoyaltyAccountEntity(account.customerId(), account.points());
    }
}
