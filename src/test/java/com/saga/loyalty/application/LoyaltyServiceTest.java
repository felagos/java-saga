package com.saga.loyalty.application;

import com.saga.loyalty.domain.LoyaltyAccount;
import com.saga.loyalty.domain.LoyaltyAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoyaltyServiceTest {

    private final LoyaltyAccountRepository repository = mock(LoyaltyAccountRepository.class);
    private LoyaltyService loyaltyService;

    @BeforeEach
    void setUp() {
        loyaltyService = new LoyaltyService(repository, 100.0);
    }

    @Test
    void earnsPointsForNewCustomer() {
        when(repository.findByCustomerId("customer-1")).thenReturn(Optional.empty());

        LoyaltyAccount result = loyaltyService.earnPoints("customer-1", 250.0);

        assertThat(result.points()).isEqualTo(2L);
        ArgumentCaptor<LoyaltyAccount> captor = ArgumentCaptor.forClass(LoyaltyAccount.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LoyaltyAccount("customer-1", 2L));
    }

    @Test
    void earnsPointsOnTopOfExistingBalance() {
        when(repository.findByCustomerId("customer-1")).thenReturn(Optional.of(new LoyaltyAccount("customer-1", 5L)));

        LoyaltyAccount result = loyaltyService.earnPoints("customer-1", 100.0);

        assertThat(result.points()).isEqualTo(6L);
    }

    @Test
    void revokesPreviouslyEarnedPoints() {
        LoyaltyAccount account = new LoyaltyAccount("customer-1", 6L);

        loyaltyService.revokePoints(account, 2L);

        verify(repository).save(new LoyaltyAccount("customer-1", 4L));
    }

    @Test
    void revokingMoreThanAvailableFails() {
        LoyaltyAccount account = new LoyaltyAccount("customer-1", 1L);

        assertThatThrownBy(() -> loyaltyService.revokePoints(account, 2L))
                .isInstanceOf(IllegalStateException.class);
    }
}
