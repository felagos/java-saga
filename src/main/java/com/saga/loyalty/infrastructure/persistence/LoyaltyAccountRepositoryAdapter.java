package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.domain.LoyaltyAccount;
import com.saga.loyalty.domain.LoyaltyAccountRepository;
import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyAccountEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LoyaltyAccountRepositoryAdapter implements LoyaltyAccountRepository {

    private final LoyaltyAccountJpaRepository jpaRepository;
    private final LoyaltyAccountPersistenceMapper mapper;

    public LoyaltyAccountRepositoryAdapter(LoyaltyAccountJpaRepository jpaRepository,
                                            LoyaltyAccountPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<LoyaltyAccount> findByCustomerId(String customerId) {
        return jpaRepository.findById(customerId).map(mapper::toDomain);
    }

    @Override
    public void save(LoyaltyAccount account) {
        LoyaltyAccountEntity entity = jpaRepository.findById(account.customerId())
                .orElseGet(() -> jpaRepository.save(mapper.toEntity(account)));
        entity.setPoints(account.points());
    }
}
