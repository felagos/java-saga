package com.saga.inventory.infrastructure.persistence;

import com.saga.inventory.domain.Stock;
import com.saga.inventory.domain.StockRepository;
import com.saga.inventory.infrastructure.persistence.entity.StockEntity;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StockRepositoryAdapter implements StockRepository {

    private final StockJpaRepository jpaRepository;
    private final StockPersistenceMapper mapper;

    public StockRepositoryAdapter(StockJpaRepository jpaRepository, StockPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Stock> findByProductId(String productId) {
        return jpaRepository.findById(productId).map(mapper::toDomain);
    }

    @Override
    public void save(Stock stock) {
        // Update the managed entity in place instead of merging a fresh instance,
        // so the @Version field reflects the row actually read in this transaction.
        StockEntity entity = jpaRepository.findById(stock.productId())
                .orElseGet(() -> jpaRepository.save(mapper.toEntity(stock)));
        entity.setAvailableQuantity(stock.availableQuantity());
    }
}
