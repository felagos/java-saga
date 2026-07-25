package com.saga.orders.application;

import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.OrderConfirmedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.events.ShippingGeneratedEvent;
import com.saga.shared.messaging.SagaEventPublisher;
import com.saga.shared.messaging.SagaEventSubscriber;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Last step of the happy path: shipping is ready, so the order can be confirmed. */
@Component
public class ConfirmOrderListener {

    private final OrderService orderService;
    private final SagaEventSubscriber subscriber;
    private final SagaEventPublisher publisher;
    private final SagaAuditService auditService;

    public ConfirmOrderListener(OrderService orderService, SagaEventSubscriber subscriber,
                                 SagaEventPublisher publisher, SagaAuditService auditService) {
        this.orderService = orderService;
        this.subscriber = subscriber;
        this.publisher = publisher;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.SHIPPING_GENERATED, ShippingGeneratedEvent.class, this::handle);
    }

    private void handle(ShippingGeneratedEvent event) {
        orderService.confirm(event.orderId());
        auditService.record(event.orderId(), "ORDER_CONFIRMED", "SUCCESS", "Order confirmed");
        publisher.publish(SagaSubjects.ORDER_CONFIRMED, new OrderConfirmedEvent(event.orderId()));
    }
}
