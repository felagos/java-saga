package com.saga.shipping.infrastructure.persistence;

import com.saga.shipping.domain.Shipment;
import com.saga.shipping.infrastructure.persistence.entity.ShipmentEntity;
import org.springframework.stereotype.Component;

@Component
public class ShipmentPersistenceMapper {

    public Shipment toDomain(ShipmentEntity entity) {
        return new Shipment(entity.getId(), entity.getProductId(), entity.getStatus());
    }

    public ShipmentEntity toEntity(Shipment shipment) {
        return new ShipmentEntity(shipment.productId(), shipment.status());
    }
}
