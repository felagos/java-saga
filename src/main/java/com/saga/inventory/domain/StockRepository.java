package com.saga.inventory.domain;

import java.util.Optional;

public interface StockRepository {

    Optional<Stock> findByProductId(String productId);

    void save(Stock stock);
}
