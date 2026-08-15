package com.saga.checkout.web;

import com.saga.checkout.application.CheckoutUseCase;
import com.saga.checkout.web.dto.CheckoutRequestDTO;
import com.saga.checkout.web.dto.CheckoutResponseDTO;
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
    public CheckoutResponseDTO checkout(@Valid @RequestBody CheckoutRequestDTO request) {
        Order order = checkoutUseCase.checkout(request.customerId(), request.productId(), request.quantity(),
                request.amount());
        return new CheckoutResponseDTO(order.id(), order.status().name());
    }
}
