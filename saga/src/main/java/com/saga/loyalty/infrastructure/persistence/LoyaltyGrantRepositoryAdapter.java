package com.saga.loyalty.infrastructure.persistence;

import com.saga.loyalty.domain.LoyaltyGrant;
import com.saga.loyalty.domain.LoyaltyGrantRepository;
import com.saga.loyalty.infrastructure.persistence.entity.LoyaltyGrantEntity;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyGrantRepositoryAdapter implements LoyaltyGrantRepository {

    private final LoyaltyGrantJpaRepository jpaRepository;
    private final LoyaltyGrantPersistenceMapper mapper;

    public LoyaltyGrantRepositoryAdapter(LoyaltyGrantJpaRepository jpaRepository, LoyaltyGrantPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public LoyaltyGrant save(LoyaltyGrant grant) {
        if (grant.id() == null) {
            LoyaltyGrantEntity saved = jpaRepository.save(mapper.toEntity(grant));
            return mapper.toDomain(saved);
        }
        LoyaltyGrantEntity entity = jpaRepository.findById(grant.id())
                .orElseThrow(() -> new IllegalStateException("LoyaltyGrant not found: " + grant.id()));
        entity.setReverted(grant.reverted());
        return mapper.toDomain(entity);
    }
}
