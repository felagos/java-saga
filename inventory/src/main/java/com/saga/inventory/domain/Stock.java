package com.saga.inventory.domain;

import com.saga.inventory.exceptions.InsufficientStockException;

public record Stock(String productId, int availableQuantity) {

    public Stock reserve(int quantity) {
        if (quantity > availableQuantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity);
        }
        return new Stock(productId, availableQuantity - quantity);
    }

    public Stock release(int quantity) {
        return new Stock(productId, availableQuantity + quantity);
    }
}
