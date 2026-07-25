package com.saga.inventory.infrastructure.persistence;

import com.saga.inventory.infrastructure.persistence.entity.StockEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class StockSeeder implements ApplicationRunner {

    private final StockJpaRepository stockJpaRepository;

    @Value("${inventory.seed.product-id}")
    private String productId;

    @Value("${inventory.seed.initial-stock}")
    private int initialStock;

    public StockSeeder(StockJpaRepository stockJpaRepository) {
        this.stockJpaRepository = stockJpaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (stockJpaRepository.findById(productId).isEmpty()) {
            stockJpaRepository.save(new StockEntity(productId, initialStock));
        }
    }
}
