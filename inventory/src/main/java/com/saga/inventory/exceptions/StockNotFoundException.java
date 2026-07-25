package com.saga.inventory.exceptions;

import com.saga.shared.exceptions.NotFoundException;

public class StockNotFoundException extends NotFoundException {

    public StockNotFoundException(String productId) {
        super("Stock not found: " + productId);
    }
}
