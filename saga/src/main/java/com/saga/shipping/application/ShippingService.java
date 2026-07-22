package com.saga.shipping.application;

import com.saga.shipping.domain.Shipment;
import com.saga.shipping.domain.ShipmentRepository;
import com.saga.shipping.domain.ShipmentStatus;
import com.saga.shipping.domain.ShippingFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShippingService {

    private final ShipmentRepository shipmentRepository;

    @Value("${shipping.simulate.fail}")
    private boolean simulateFail;

    public ShippingService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    public Shipment generate(String productId) {
        if (simulateFail) {
            throw new ShippingFailedException(productId);
        }
        return shipmentRepository.save(new Shipment(null, productId, ShipmentStatus.GENERATED));
    }

    public void cancel(Shipment shipment) {
        shipmentRepository.save(shipment.withStatus(ShipmentStatus.CANCELLED));
    }
}
