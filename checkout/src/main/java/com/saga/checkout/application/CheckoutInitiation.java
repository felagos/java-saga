package com.saga.checkout.application;

import com.saga.orders.domain.OrderStatus;

public record CheckoutInitiation(Long orderId, Long paymentId, OrderStatus status) {
}
