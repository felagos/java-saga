package com.saga.loyalty.application;

import com.saga.loyalty.domain.LoyaltyGrant;
import com.saga.loyalty.domain.LoyaltyGrantRepository;
import org.springframework.stereotype.Component;

@Component
public class LoyaltyService {

    private static final double POINTS_PER_CURRENCY_UNIT = 0.1;

    private final LoyaltyGrantRepository loyaltyGrantRepository;

    public LoyaltyService(LoyaltyGrantRepository loyaltyGrantRepository) {
        this.loyaltyGrantRepository = loyaltyGrantRepository;
    }

    public LoyaltyGrant grant(String customerId, double amount) {
        int points = (int) (amount * POINTS_PER_CURRENCY_UNIT);
        return loyaltyGrantRepository.save(new LoyaltyGrant(null, customerId, points, false));
    }

    public void revert(LoyaltyGrant grant) {
        loyaltyGrantRepository.save(grant.markReverted());
    }
}
