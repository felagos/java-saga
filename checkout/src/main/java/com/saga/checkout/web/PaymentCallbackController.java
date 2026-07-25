package com.saga.checkout.web;

import com.saga.checkout.application.CheckoutUseCase;
import com.saga.checkout.web.dto.PaymentCallbackRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Simulates the payment gateway's webhook: it resolves a PENDING payment created by /checkout. */
@RestController
public class PaymentCallbackController {

    private final CheckoutUseCase checkoutUseCase;

    public PaymentCallbackController(CheckoutUseCase checkoutUseCase) {
        this.checkoutUseCase = checkoutUseCase;
    }

    @PostMapping("/payments/{paymentId}/callback")
    public void callback(@PathVariable Long paymentId, @RequestBody PaymentCallbackRequest request) {
        checkoutUseCase.handlePaymentResult(paymentId, request.approved());
    }
}
