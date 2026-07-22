package com.saga.orders.application;

import com.saga.checkout.orchestrator.SagaStep;

/**
 * Per-checkout adapter, not a Spring bean. Last step in the saga: nothing runs after it, so
 * there's nothing to compensate — it can only be reached once every prior step has already
 * succeeded, and once it succeeds the checkout itself is done.
 */
public class ConfirmOrderStep implements SagaStep {

    private final OrderService orderService;
    private final CreateOrderStep createOrderStep;

    public ConfirmOrderStep(OrderService orderService, CreateOrderStep createOrderStep) {
        this.orderService = orderService;
        this.createOrderStep = createOrderStep;
    }

    @Override
    public void execute() {
        orderService.confirm(createOrderStep.order());
    }

    @Override
    public void compensate() {
        // Intentionally empty: the last step in the saga has nothing after it that can fail.
    }
}
