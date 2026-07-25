package com.saga.payments.exceptions;

import com.saga.shared.exceptions.NotFoundException;

public class PaymentNotFoundException extends NotFoundException {

    public PaymentNotFoundException(Long paymentId) {
        super("Payment not found: " + paymentId);
    }
}
