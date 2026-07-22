package com.saga.shipping.infrastructure.persistence.entity;

import com.saga.shipping.domain.ShipmentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shipment")
public class ShipmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String productId;

    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;

    protected ShipmentEntity() {
    }

    public ShipmentEntity(String productId, ShipmentStatus status) {
        this.productId = productId;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getProductId() {
        return productId;
    }

    public ShipmentStatus getStatus() {
        return status;
    }

    public void setStatus(ShipmentStatus status) {
        this.status = status;
    }
}
