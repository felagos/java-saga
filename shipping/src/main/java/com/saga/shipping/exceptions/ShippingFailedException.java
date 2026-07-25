package com.saga.shipping.exceptions;

public class ShippingFailedException extends RuntimeException {

    public ShippingFailedException(String productId) {
        super("Warehouse unreachable while generating shipment for product " + productId);
    }
}
