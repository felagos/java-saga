package com.saga.orders.domain;

public record Order(Long id, String customerId, String productId, int quantity, double amount, OrderStatus status) {
}
