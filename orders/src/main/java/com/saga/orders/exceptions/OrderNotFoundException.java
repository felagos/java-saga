package com.saga.orders.exceptions;

import com.saga.shared.exceptions.NotFoundException;

public class OrderNotFoundException extends NotFoundException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }
}
