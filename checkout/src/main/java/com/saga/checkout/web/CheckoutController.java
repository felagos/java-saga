package com.saga.checkout.web;

import com.saga.checkout.application.CheckoutInitiation;
import com.saga.checkout.application.CheckoutUseCase;
import com.saga.checkout.web.dto.CheckoutRequest;
import com.saga.checkout.web.dto.CheckoutResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final CheckoutUseCase checkoutUseCase;

    public CheckoutController(CheckoutUseCase checkoutUseCase) {
        this.checkoutUseCase = checkoutUseCase;
    }

    @PostMapping("/checkout")
    public ResponseEntity<CheckoutResponse> checkout(@Valid @RequestBody CheckoutRequest request) {
        CheckoutInitiation initiation = checkoutUseCase.checkout(request.customerId(), request.productId(),
                request.quantity(), request.amount());
        CheckoutResponse response = new CheckoutResponse(initiation.orderId(), initiation.paymentId(),
                initiation.status().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
