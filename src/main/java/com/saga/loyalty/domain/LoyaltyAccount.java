package com.saga.loyalty.domain;

public record LoyaltyAccount(String customerId, long points) {

    public LoyaltyAccount earn(long pointsEarned) {
        return new LoyaltyAccount(customerId, points + pointsEarned);
    }

    public LoyaltyAccount revoke(long pointsRevoked) {
        if (pointsRevoked > points) {
            throw new IllegalStateException("Insufficient loyalty points to revoke");
        }
        return new LoyaltyAccount(customerId, points - pointsRevoked);
    }
}
