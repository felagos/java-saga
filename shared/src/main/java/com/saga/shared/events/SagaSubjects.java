package com.saga.shared.events;

public final class SagaSubjects {

    public static final String PAYMENT_INITIATED = "saga.payment.initiated";
    public static final String PAYMENT_APPROVED = "saga.payment.approved";
    public static final String PAYMENT_REJECTED = "saga.payment.rejected";
    public static final String SHIPPING_GENERATED = "saga.shipping.generated";
    public static final String SHIPPING_FAILED = "saga.shipping.failed";
    public static final String ORDER_CONFIRMED = "saga.order.confirmed";

    private SagaSubjects() {
    }
}
