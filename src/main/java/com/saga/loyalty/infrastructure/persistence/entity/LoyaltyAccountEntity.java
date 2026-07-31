package com.saga.loyalty.infrastructure.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "loyalty_account")
public class LoyaltyAccountEntity {

    @Id
    private String customerId;

    private long points;

    protected LoyaltyAccountEntity() {
    }

    public LoyaltyAccountEntity(String customerId, long points) {
        this.customerId = customerId;
        this.points = points;
    }

    public String getCustomerId() {
        return customerId;
    }

    public long getPoints() {
        return points;
    }

    public void setPoints(long points) {
        this.points = points;
    }
}
