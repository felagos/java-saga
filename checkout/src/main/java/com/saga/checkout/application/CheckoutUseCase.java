package com.saga.checkout.application;

import com.saga.checkout.exceptions.CheckoutInitiationException;
import com.saga.checkout.orchestrator.SagaOrchestrator;
import com.saga.checkout.orchestrator.SagaStep;
import com.saga.inventory.application.InventoryService;
import com.saga.inventory.application.ReserveStockStep;
import com.saga.orders.application.ConfirmOrderStep;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import com.saga.payments.application.PaymentService;
import com.saga.payments.domain.Payment;
import com.saga.shipping.application.GenerateShippingStep;
import com.saga.shipping.application.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Defines the checkout saga. Payment is charged by an external gateway that confirms
 * asynchronously (hours later, via {@link #handlePaymentResult}), so the saga now spans two HTTP
 * requests instead of one:
 * <p>
 * Phase 1 ({@link #checkout}, synchronous): reserve stock, create the order as
 * {@code PENDING_PAYMENT}, hand the charge to the gateway. Responds without knowing the outcome.
 * <p>
 * Phase 2 ({@link #handlePaymentResult}, triggered by the gateway's callback): on rejection,
 * compensate what phase 1 did (release stock, cancel the order). On approval, run the rest of the
 * saga (generate shipping, confirm the order) through the same LIFO {@link SagaOrchestrator}; if
 * that fails, compensate everything, including the now-settled payment (refund).
 */
@Component
public class CheckoutUseCase {

    private static final Logger log = LoggerFactory.getLogger(CheckoutUseCase.class);

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

    public CheckoutInitiation checkout(String customerId, String productId, int quantity, double amount) {
        ReserveStockStep reserveStock = new ReserveStockStep(inventoryService, productId, quantity);
        reserveStock.execute();

        try {
            Order order = orderService.createPending(customerId, productId, quantity, amount);
            Payment payment = paymentService.initiate(order.id(), customerId, amount);
            
            return new CheckoutInitiation(order.id(), payment.id(), order.status());
        } catch (RuntimeException e) {
            reserveStock.compensate();
            throw new CheckoutInitiationException(e);
        }
    }

    public void handlePaymentResult(Long paymentId, boolean approved) {
        Payment payment = paymentService.findById(paymentId);
        Order order = orderService.findById(payment.orderId());

        if (!approved) {
            paymentService.reject(payment);
            inventoryService.release(order.productId(), order.quantity());
            orderService.cancel(order.id());
            return;
        }

        Payment charged = paymentService.confirm(payment);
        List<SagaStep> steps = List.of(
                new GenerateShippingStep(shippingService, order.productId()),
                new ConfirmOrderStep(orderService, order.id())
        );
        try {
            sagaOrchestrator.run(steps);
        } catch (RuntimeException e) {
            log.warn("Post-payment saga failed for order {}, compensating: {}", order.id(), e.getMessage());
            paymentService.refund(charged);
            inventoryService.release(order.productId(), order.quantity());
            orderService.cancel(order.id());
        }
    }
}
