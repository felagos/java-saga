package com.saga.inventory.infrastructure.persistence;

import com.saga.inventory.domain.Stock;
import com.saga.inventory.infrastructure.persistence.entity.StockEntity;
import org.springframework.stereotype.Component;

@Component
public class StockPersistenceMapper {

    public Stock toDomain(StockEntity entity) {
        return new Stock(entity.getProductId(), entity.getAvailableQuantity());
    }

    public StockEntity toEntity(Stock stock) {
        return new StockEntity(stock.productId(), stock.availableQuantity());
    }
}
