package com.saga.orders.application;

import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.PaymentRejectedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.events.ShippingFailedEvent;
import com.saga.shared.messaging.SagaEventSubscriber;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Compensation: cancel the order when the saga fails, whichever step failed it. */
@Component
public class CancelOrderListener {

    private final OrderService orderService;
    private final SagaEventSubscriber subscriber;
    private final SagaAuditService auditService;

    public CancelOrderListener(OrderService orderService, SagaEventSubscriber subscriber,
                                SagaAuditService auditService) {
        this.orderService = orderService;
        this.subscriber = subscriber;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.PAYMENT_REJECTED, PaymentRejectedEvent.class,
                event -> cancel(event.orderId(), "payment rejected"));
        subscriber.subscribe(SagaSubjects.SHIPPING_FAILED, ShippingFailedEvent.class,
                event -> cancel(event.orderId(), "shipping failed"));
    }

    private void cancel(Long orderId, String reason) {
        orderService.cancel(orderId);
        auditService.record(orderId, "ORDER_CANCELLED", "COMPENSATED", "Order cancelled (" + reason + ")");
    }
}
