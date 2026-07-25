package com.saga.payments.application;

import com.saga.payments.domain.Payment;
import com.saga.shared.audit.SagaAuditService;
import com.saga.shared.events.PaymentApprovedEvent;
import com.saga.shared.events.PaymentInitiatedEvent;
import com.saga.shared.events.PaymentRejectedEvent;
import com.saga.shared.events.SagaSubjects;
import com.saga.shared.messaging.SagaEventPublisher;
import com.saga.shared.messaging.SagaEventSubscriber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stands in for the payment gateway: reads the initiated charge off NATS and resolves it. */
@Component
public class PaymentInitiatedListener {

    private final PaymentService paymentService;
    private final SagaEventSubscriber subscriber;
    private final SagaEventPublisher publisher;
    private final SagaAuditService auditService;

    @Value("${payments.simulate.reject}")
    private boolean simulateReject;

    public PaymentInitiatedListener(PaymentService paymentService, SagaEventSubscriber subscriber,
                                     SagaEventPublisher publisher, SagaAuditService auditService) {
        this.paymentService = paymentService;
        this.subscriber = subscriber;
        this.publisher = publisher;
        this.auditService = auditService;
    }

    @PostConstruct
    void subscribe() {
        subscriber.subscribe(SagaSubjects.PAYMENT_INITIATED, PaymentInitiatedEvent.class, this::handle);
    }

    private void handle(PaymentInitiatedEvent event) {
        Payment payment = paymentService.findById(event.paymentId());

        if (simulateReject) {
            paymentService.reject(payment);
            auditService.record(event.orderId(), "PAYMENT_REJECTED", "FAILED", "Gateway declined payment " + event.paymentId());
            publisher.publish(SagaSubjects.PAYMENT_REJECTED,
                    new PaymentRejectedEvent(event.orderId(), event.paymentId(), event.productId(), event.quantity()));
            return;
        }

        paymentService.confirm(payment);
        auditService.record(event.orderId(), "PAYMENT_APPROVED", "SUCCESS", "Payment " + event.paymentId() + " charged");
        publisher.publish(SagaSubjects.PAYMENT_APPROVED, new PaymentApprovedEvent(event.orderId(), event.paymentId(),
                event.productId(), event.quantity(), event.amount()));
    }
}
