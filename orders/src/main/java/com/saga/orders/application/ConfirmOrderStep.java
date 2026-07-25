package com.saga.orders.application;

import com.saga.checkout.orchestrator.SagaStep;

/**
 * Per-checkout adapter, not a Spring bean. Last step of the phase-2 (post-payment) sub-saga: the
 * order transitions PENDING_PAYMENT -> CONFIRMED. Nothing runs after it that can fail, so there is
 * nothing to compensate.
 */
public class ConfirmOrderStep implements SagaStep {

    private final OrderService orderService;
    private final Long orderId;

    public ConfirmOrderStep(OrderService orderService, Long orderId) {
        this.orderService = orderService;
        this.orderId = orderId;
    }

    @Override
    public void execute() {
        orderService.confirm(orderId);
    }

    @Override
    public void compensate() {
        // Intentionally empty: the last step in the sub-saga has nothing after it that can fail.
    }
}
