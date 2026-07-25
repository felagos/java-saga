package com.saga.payments.application;

import com.saga.payments.domain.Payment;
import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.events.ShippingFailedEvent;
import com.saga.shared.messaging.SagaEventSubscriber;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Compensation: shipping couldn't be generated after the charge went through, refund it. */
@Component
public class ShippingFailedRefundListener {

    private final PaymentService paymentService;
    private final SagaEventSubscriber subscriber;
    private final SagaAuditService auditService;

    public ShippingFailedRefundListener(PaymentService paymentService, SagaEventSubscriber subscriber,
                                         SagaAuditService auditService) {
        this.paymentService = paymentService;
        this.subscriber = subscriber;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.SHIPPING_FAILED, ShippingFailedEvent.class, this::handle);
    }

    private void handle(ShippingFailedEvent event) {
        Payment payment = paymentService.findById(event.paymentId());
        paymentService.refund(payment);
        auditService.record(event.orderId(), "PAYMENT_REFUNDED", "COMPENSATED",
                "Refunded payment " + event.paymentId() + " after shipping failure: " + event.reason());
    }
}
