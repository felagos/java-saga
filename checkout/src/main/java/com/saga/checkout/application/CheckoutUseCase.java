package com.saga.checkout.application;

import com.saga.checkout.exceptions.CheckoutInitiationException;
import com.saga.inventory.application.InventoryService;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import com.saga.payments.application.PaymentService;
import com.saga.payments.domain.Payment;
import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.PaymentInitiatedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.messaging.SagaEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Phase 1 of the checkout saga (synchronous, {@code POST /checkout}): reserve stock, create the
 * order as {@code PENDING_PAYMENT}, hand the charge to the gateway via NATS. Everything past that
 * point (payment resolution, shipping, order confirmation, and their compensations) is
 * choreographed independently by each module's own NATS listener — see
 * {@code payments/inventory/shipping/orders application.*Listener} classes. There is no phase 2
 * here anymore: nothing in this class waits for or drives the rest of the saga.
 */
@Component
public class CheckoutUseCase {

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final PaymentService paymentService;
    private final SagaEventPublisher eventPublisher;
    private final SagaAuditService auditService;

    public CheckoutUseCase(OrderService orderService, InventoryService inventoryService,
                            PaymentService paymentService, SagaEventPublisher eventPublisher,
                            SagaAuditService auditService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    public CheckoutInitiation checkout(String customerId, String productId, int quantity, double amount) {
        inventoryService.reserve(productId, quantity);

        try {
            Order order = orderService.createPending(customerId, productId, quantity, amount);
            auditService.record(order.id(), "STOCK_RESERVED", "SUCCESS", "Reserved " + quantity + " of " + productId);
            auditService.record(order.id(), "ORDER_CREATED", "SUCCESS", "Order created as PENDING_PAYMENT");

            Payment payment = paymentService.initiate(order.id(), customerId, amount);
            auditService.record(order.id(), "PAYMENT_INITIATED", "SUCCESS", "Payment " + payment.id() + " handed to gateway");

            eventPublisher.publish(SagaSubjects.PAYMENT_INITIATED,
                    new PaymentInitiatedEvent(order.id(), payment.id(), customerId, productId, quantity, amount));

            return new CheckoutInitiation(order.id(), payment.id(), order.status());
        } catch (RuntimeException e) {
            inventoryService.release(productId, quantity);
            throw new CheckoutInitiationException(e);
        }
    }
}
