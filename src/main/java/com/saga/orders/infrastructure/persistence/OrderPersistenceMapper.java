package com.saga.orders.infrastructure.persistence;

import com.saga.orders.domain.Order;
import com.saga.orders.infrastructure.persistence.entity.OrderEntity;
import org.springframework.stereotype.Component;

@Component
public class OrderPersistenceMapper {

    public Order toDomain(OrderEntity entity) {
        return new Order(entity.getId(), entity.getCustomerId(), entity.getProductId(), entity.getQuantity(),
                entity.getAmount(), entity.getStatus());
    }

    public OrderEntity toEntity(Order order) {
        return new OrderEntity(order.customerId(), order.productId(), order.quantity(), order.amount(),
                order.status());
    }
}
