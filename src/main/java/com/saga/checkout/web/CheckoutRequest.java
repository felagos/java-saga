package com.saga.checkout.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CheckoutRequest(
        @NotBlank String customerId,
        @NotBlank String productId,
        @Positive int quantity,
        @Positive double amount) {
}
