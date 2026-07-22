package com.saga.inventory.application;

import com.saga.bff.orchestrator.SagaStep;

/** Per-checkout adapter, not a Spring bean: carries the request's productId/quantity. */
public class ReserveStockStep implements SagaStep {

    private final InventoryService inventoryService;
    private final String productId;
    private final int quantity;

    public ReserveStockStep(InventoryService inventoryService, String productId, int quantity) {
        this.inventoryService = inventoryService;
        this.productId = productId;
        this.quantity = quantity;
    }

    @Override
    public void execute() {
        inventoryService.reserve(productId, quantity);
    }

    @Override
    public void compensate() {
        inventoryService.release(productId, quantity);
    }
}
