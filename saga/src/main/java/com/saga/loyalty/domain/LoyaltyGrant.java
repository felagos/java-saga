package com.saga.loyalty.domain;

public record LoyaltyGrant(Long id, String customerId, int points, boolean reverted) {

    public LoyaltyGrant markReverted() {
        return new LoyaltyGrant(id, customerId, points, true);
    }
}
