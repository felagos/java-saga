package com.saga.shipping.exceptions;

import com.saga.shared.exceptions.NotFoundException;

public class ShipmentNotFoundException extends NotFoundException {

    public ShipmentNotFoundException(Long shipmentId) {
        super("Shipment not found: " + shipmentId);
    }
}
