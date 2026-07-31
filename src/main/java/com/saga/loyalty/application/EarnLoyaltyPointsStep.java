package com.saga.loyalty.application;

import com.saga.checkout.orchestrator.SagaStep;
import com.saga.loyalty.domain.LoyaltyAccount;

/** Per-checkout adapter, not a Spring bean: carries the updated account for compensate(). */
public class EarnLoyaltyPointsStep implements SagaStep {

    private final LoyaltyService loyaltyService;
    private final String customerId;
    private final double amount;

    private LoyaltyAccount account;
    private long earnedPoints;

    public EarnLoyaltyPointsStep(LoyaltyService loyaltyService, String customerId, double amount) {
        this.loyaltyService = loyaltyService;
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public void execute() {
        earnedPoints = loyaltyService.calculatePoints(amount);
        account = loyaltyService.earnPoints(customerId, amount);
    }

    @Override
    public void compensate() {
        loyaltyService.revokePoints(account, earnedPoints);
    }
}
