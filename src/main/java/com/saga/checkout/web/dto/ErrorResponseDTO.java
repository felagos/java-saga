package com.saga.checkout.web.dto;

public class ErrorResponseDTO {
    private final String reason;

    public ErrorResponseDTO() {
        this(null);
    }

    public ErrorResponseDTO(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
