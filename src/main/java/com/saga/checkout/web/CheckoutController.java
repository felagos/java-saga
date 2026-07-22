package com.saga.checkout.web;

import com.saga.checkout.application.CheckoutUseCase;
import com.saga.orders.domain.Order;
import jakarta.validation.Valid;
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
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        Order order = checkoutUseCase.checkout(request.customerId(), request.productId(), request.quantity(),
                request.amount());
        return new CheckoutResponse(order.id(), order.status().name());
    }
}
