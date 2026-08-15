package com.saga.loyalty.application;

import com.saga.loyalty.domain.LoyaltyAccount;
import com.saga.loyalty.domain.LoyaltyAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoyaltyService {

    private final LoyaltyAccountRepository loyaltyAccountRepository;
    private final double pointsPerCurrencyUnit;

    public LoyaltyService(LoyaltyAccountRepository loyaltyAccountRepository,
                           @Value("${loyalty.points-per-currency-unit:100.0}") double pointsPerCurrencyUnit) {
        this.loyaltyAccountRepository = loyaltyAccountRepository;
        this.pointsPerCurrencyUnit = pointsPerCurrencyUnit;
    }

    @Transactional
    public LoyaltyAccount earnPoints(String customerId, double amount) {
        long earnedPoints = calculatePoints(amount);
        LoyaltyAccount account = loyaltyAccountRepository.findByCustomerId(customerId)
                .orElseGet(() -> new LoyaltyAccount(customerId, 0L));
        LoyaltyAccount updated = account.earn(earnedPoints);
        loyaltyAccountRepository.save(updated);
        return updated;
    }

    @Transactional
    public void revokePoints(LoyaltyAccount account, long points) {
        loyaltyAccountRepository.save(account.revoke(points));
    }

    public long calculatePoints(double amount) {
        return (long) (amount / pointsPerCurrencyUnit);
    }
}
