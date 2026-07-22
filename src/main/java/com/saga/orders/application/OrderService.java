package com.saga.orders.application;

import com.saga.orders.domain.Order;
import com.saga.orders.domain.OrderRepository;
import com.saga.orders.domain.OrderStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Order create(String customerId, String productId, int quantity, double amount) {
        return orderRepository.save(new Order(null, customerId, productId, quantity, amount, OrderStatus.CONFIRMED));
    }
}
