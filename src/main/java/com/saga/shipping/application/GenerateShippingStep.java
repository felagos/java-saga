package com.saga.shipping.application;

import com.saga.checkout.orchestrator.SagaStep;
import com.saga.shipping.domain.Shipment;

/** Per-checkout adapter, not a Spring bean: carries the created Shipment for compensate(). */
public class GenerateShippingStep implements SagaStep {

    private final ShippingService shippingService;
    private final String productId;

    private Shipment shipment;

    public GenerateShippingStep(ShippingService shippingService, String productId) {
        this.shippingService = shippingService;
        this.productId = productId;
    }

    @Override
    public void execute() {
        shipment = shippingService.generate(productId);
    }

    @Override
    public void compensate() {
        shippingService.cancel(shipment);
    }
}
