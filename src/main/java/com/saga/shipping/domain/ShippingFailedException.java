package com.saga.shipping.domain;

public class ShippingFailedException extends RuntimeException {

    public ShippingFailedException(String productId) {
        super("Warehouse unreachable while generating shipment for product " + productId);
    }
}
