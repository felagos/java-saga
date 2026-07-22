package com.saga.loyalty.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loyalty_grant")
public class LoyaltyGrantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private int points;
    private boolean reverted;

    protected LoyaltyGrantEntity() {
    }

    public LoyaltyGrantEntity(String customerId, int points, boolean reverted) {
        this.customerId = customerId;
        this.points = points;
        this.reverted = reverted;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public int getPoints() {
        return points;
    }

    public boolean isReverted() {
        return reverted;
    }

    public void setReverted(boolean reverted) {
        this.reverted = reverted;
    }
}
