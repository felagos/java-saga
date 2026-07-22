package com.saga.checkout.application;

import com.saga.inventory.application.InventoryService;
import com.saga.inventory.application.ReserveStockStep;
import com.saga.checkout.orchestrator.SagaOrchestrator;
import com.saga.checkout.orchestrator.SagaStep;
import com.saga.orders.application.CreateOrderStep;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import com.saga.payments.application.ChargePaymentStep;
import com.saga.payments.application.PaymentService;
import com.saga.shipping.application.GenerateShippingStep;
import com.saga.shipping.application.ShippingService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Defines the checkout saga: which steps, in which order. The LIFO compensation mechanics live
 * in SagaOrchestrator; this class only knows what the steps are.
 */
@Component
public class CheckoutUseCase {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final ShippingService shippingService;
    private final SagaOrchestrator sagaOrchestrator;

    public CheckoutUseCase(OrderService orderService, InventoryService inventoryService,
                            PaymentService paymentService,
                            ShippingService shippingService, SagaOrchestrator sagaOrchestrator) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.shippingService = shippingService;
        this.sagaOrchestrator = sagaOrchestrator;
    }

    public Order checkout(String customerId, String productId, int quantity, double amount) {
        CreateOrderStep createOrder = new CreateOrderStep(orderService, customerId, productId, quantity, amount);
        List<SagaStep> steps = List.of(
                new ReserveStockStep(inventoryService, productId, quantity),
                new ChargePaymentStep(paymentService, customerId, amount),
                new GenerateShippingStep(shippingService, productId),
                createOrder
        );

        sagaOrchestrator.run(steps);

        return createOrder.order();
    }
}
