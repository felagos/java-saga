package com.saga.checkout.web;

import com.saga.checkout.web.dto.OrderResponseDTO;
import com.saga.orders.domain.OrderRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(order -> ResponseEntity.ok(new OrderResponseDTO(order.id(), order.customerId(), order.productId(),
                        order.quantity(), order.amount(), order.status().name())))
                .orElse(ResponseEntity.notFound().build());
    }
}

