package com.saga.orders.application;

import com.saga.checkout.orchestrator.SagaStep;
import com.saga.orders.domain.Order;

/**
 * Per-checkout adapter, not a Spring bean: it carries request state (the inputs, then the
 * created Order) so compensate() and the downstream ConfirmOrderStep can use the result.
 */
public class CreateOrderStep implements SagaStep {

    private final OrderService orderService;
    private final String customerId;
    private final String productId;
    private final int quantity;
    private final double amount;

    private Order order;

    public CreateOrderStep(OrderService orderService, String customerId, String productId, int quantity,
                            double amount) {
        this.orderService = orderService;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    @Override
    public void execute() {
        order = orderService.create(customerId, productId, quantity, amount);
    }

    @Override
    public void compensate() {
        orderService.cancel(order);
    }

    public Order order() {
        return order;
    }
}
