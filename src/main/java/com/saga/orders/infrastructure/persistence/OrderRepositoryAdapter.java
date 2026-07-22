package com.saga.orders.infrastructure.persistence;

import com.saga.orders.domain.Order;
import com.saga.orders.domain.OrderRepository;
import com.saga.orders.infrastructure.persistence.entity.OrderEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderRepositoryAdapter implements OrderRepository {

    private final OrderJpaRepository jpaRepository;
    private final OrderPersistenceMapper mapper;

    public OrderRepositoryAdapter(OrderJpaRepository jpaRepository, OrderPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Order save(Order order) {
        if (order.id() == null) {
            OrderEntity saved = jpaRepository.save(mapper.toEntity(order));
            return mapper.toDomain(saved);
        }
        OrderEntity entity = jpaRepository.findById(order.id())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + order.id()));
        entity.setStatus(order.status());
        return mapper.toDomain(entity);
    }
}
