package com.saga.payments.infrastructure.persistence.entity;

import com.saga.payments.domain.PaymentStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerId;
    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    protected PaymentEntity() {
    }

    public PaymentEntity(String customerId, double amount, PaymentStatus status) {
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public double getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }
}
