package com.saga.inventory.application;

import com.saga.inventory.domain.InsufficientStockException;
import com.saga.inventory.domain.Stock;
import com.saga.inventory.domain.StockRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryService {

    private final StockRepository stockRepository;

    public InventoryService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    // ponytail: relies on StockEntity's @Version to fail the transaction on a concurrent
    // reserve for the same product (mapped to 409 by CheckoutExceptionHandler) instead of
    // silently overselling. No retry: a racing client just sees the conflict and can resubmit.
    // Upgrade to a conditional UPDATE (SET qty = qty - :n WHERE qty >= :n) if retries matter.
    @Transactional
    public void reserve(String productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new InsufficientStockException(productId, quantity, 0));
        stockRepository.save(stock.reserve(quantity));
    }

    @Transactional
    public void release(String productId, int quantity) {
        Stock stock = stockRepository.findByProductId(productId)
                .orElseThrow(() -> new IllegalStateException("Stock not found: " + productId));
        stockRepository.save(stock.release(quantity));
    }
}
