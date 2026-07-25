package com.saga.inventory.application;

import com.saga.inventory.exceptions.InsufficientStockException;
import com.saga.inventory.domain.Stock;
import com.saga.inventory.exceptions.StockNotFoundException;
import com.saga.inventory.domain.StockRepository;
import org.springframework.stereotype.Component;

@Component
public class InventoryService {

    private final StockRepository stockRepository;

    public InventoryService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public void reserve(String productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new InsufficientStockException(productId, quantity, 0));
        stockRepository.save(stock.reserve(quantity));
    }

    public void release(String productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new StockNotFoundException(productId));
        stockRepository.save(stock.release(quantity));
    }
}
