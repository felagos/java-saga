package com.saga.shipping.domain;

public record Shipment(Long id, String productId, ShipmentStatus status) {

    public Shipment withStatus(ShipmentStatus newStatus) {
        return new Shipment(id, productId, newStatus);
    }
}
