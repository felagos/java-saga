package com.saga.checkout.web.dto;

public class CheckoutResponseDTO {
    private final Long orderId;
    private final String status;

    public CheckoutResponseDTO() {
        this(null, null);
    }

    public CheckoutResponseDTO(Long orderId, String status) {
        this.orderId = orderId;
        this.status = status;
    }

    public Long orderId() {
        return orderId;
    }

    public String status() {
        return status;
    }
}
