package com.saga.inventory.application;

import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.PaymentRejectedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.events.ShippingFailedEvent;
import com.saga.shared.messaging.SagaEventSubscriber;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Compensation: release the reserved stock when the saga fails, whichever step failed it. */
@Component
public class ReleaseStockListener {

    private final InventoryService inventoryService;
    private final SagaEventSubscriber subscriber;
    private final SagaAuditService auditService;

    public ReleaseStockListener(InventoryService inventoryService, SagaEventSubscriber subscriber,
                                 SagaAuditService auditService) {
        this.inventoryService = inventoryService;
        this.subscriber = subscriber;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.PAYMENT_REJECTED, PaymentRejectedEvent.class,
                event -> release(event.orderId(), event.productId(), event.quantity(), "payment rejected"));
        subscriber.subscribe(SagaSubjects.SHIPPING_FAILED, ShippingFailedEvent.class,
                event -> release(event.orderId(), event.productId(), event.quantity(), "shipping failed"));
    }

    private void release(Long orderId, String productId, int quantity, String reason) {
        inventoryService.release(productId, quantity);
        auditService.record(orderId, "STOCK_RELEASED", "COMPENSATED", "Released " + quantity + " of "
                + productId + " (" + reason + ")");
    }
}
