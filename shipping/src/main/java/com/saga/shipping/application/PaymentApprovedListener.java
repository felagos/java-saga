package com.saga.shipping.application;

import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.PaymentApprovedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.events.ShippingFailedEvent;
import com.saga.shared.events.ShippingGeneratedEvent;
import com.saga.shared.messaging.SagaEventPublisher;
import com.saga.shared.messaging.SagaEventSubscriber;
import com.saga.shipping.exceptions.ShippingFailedException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class PaymentApprovedListener {

    private final ShippingService shippingService;
    private final SagaEventSubscriber subscriber;
    private final SagaEventPublisher publisher;
    private final SagaAuditService auditService;

    public PaymentApprovedListener(ShippingService shippingService, SagaEventSubscriber subscriber,
                                    SagaEventPublisher publisher, SagaAuditService auditService) {
        this.shippingService = shippingService;
        this.subscriber = subscriber;
        this.publisher = publisher;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.PAYMENT_APPROVED, PaymentApprovedEvent.class, this::handle);
    }

    private void handle(PaymentApprovedEvent event) {
        try {
            shippingService.generate(event.productId());
            auditService.record(event.orderId(), "SHIPPING_GENERATED", "SUCCESS", "Shipment generated for " + event.productId());
            
            publisher.publish(SagaSubjects.SHIPPING_GENERATED, new ShippingGeneratedEvent(event.orderId(),
                    event.paymentId(), event.productId(), event.quantity(), event.amount()));
        } catch (ShippingFailedException e) {
            auditService.record(event.orderId(), "SHIPPING_GENERATED", "FAILED", e.getMessage());
            publisher.publish(SagaSubjects.SHIPPING_FAILED, new ShippingFailedEvent(event.orderId(), event.paymentId(),
                    event.productId(), event.quantity(), e.getMessage()));
        }
    }
}
