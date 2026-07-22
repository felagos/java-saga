package com.saga.loyalty.application;

import com.saga.loyalty.domain.LoyaltyGrant;
import com.saga.orchestrator.SagaStep;

/** Per-checkout adapter, not a Spring bean: carries the created LoyaltyGrant for compensate(). */
public class GrantLoyaltyPointsStep implements SagaStep {

    private final LoyaltyService loyaltyService;
    private final String customerId;
    private final double amount;

    private LoyaltyGrant grant;

    public GrantLoyaltyPointsStep(LoyaltyService loyaltyService, String customerId, double amount) {
        this.loyaltyService = loyaltyService;
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public void execute() {
        grant = loyaltyService.grant(customerId, amount);
    }

    @Override
    public void compensate() {
        loyaltyService.revert(grant);
    }
}
