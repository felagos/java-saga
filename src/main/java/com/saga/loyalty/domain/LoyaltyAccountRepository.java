package com.saga.loyalty.domain;

import java.util.Optional;

public interface LoyaltyAccountRepository {

    Optional<LoyaltyAccount> findByCustomerId(String customerId);

    void save(LoyaltyAccount account);
}
