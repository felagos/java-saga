package com.saga.checkout.web;

import com.saga.checkout.web.dto.OrderResponse;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Lets clients poll order status while the payment is PENDING — no sync response covers that anymore. */
@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        Order order = orderService.findById(orderId);
        return new OrderResponse(order.id(), order.status().name());
    }
}
