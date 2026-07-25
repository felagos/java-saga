package com.saga.checkout.web.dto;

public record CheckoutResponse(Long orderId, Long paymentId, String status) {
}
