package com.saga.checkout.application;

import com.saga.checkout.orchestrator.SagaOrchestrator;
import com.saga.inventory.application.InventoryService;
import com.saga.loyalty.application.LoyaltyService;
import com.saga.loyalty.domain.LoyaltyAccount;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import com.saga.orders.domain.OrderStatus;
import com.saga.payments.application.PaymentService;
import com.saga.payments.domain.Payment;
import com.saga.payments.domain.PaymentRejectedException;
import com.saga.payments.domain.PaymentStatus;
import com.saga.shipping.application.ShippingService;
import com.saga.shipping.domain.Shipment;
import com.saga.shipping.domain.ShipmentStatus;
import com.saga.shipping.domain.ShippingFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the checkout saga with mocked domain services (no DB) to verify that
 * EarnLoyaltyPointsStep participates correctly in LIFO compensation.
 */
class CheckoutUseCaseLoyaltyTest {

    private final OrderService orderService = mock(OrderService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final LoyaltyService loyaltyService = mock(LoyaltyService.class);
    private final ShippingService shippingService = mock(ShippingService.class);
    private final CheckoutUseCase checkoutUseCase = new CheckoutUseCase(
            orderService, inventoryService, paymentService, loyaltyService, shippingService, new SagaOrchestrator());

    private static final String CUSTOMER_ID = "customer-1";
    private static final String PRODUCT_ID = "sku-1";
    private static final double AMOUNT = 250.0;

    @BeforeEach
    void setUp() {
        when(paymentService.charge(CUSTOMER_ID, AMOUNT))
                .thenReturn(new Payment(1L, CUSTOMER_ID, AMOUNT, PaymentStatus.CHARGED));
        when(loyaltyService.calculatePoints(AMOUNT)).thenReturn(2L);
        when(loyaltyService.earnPoints(CUSTOMER_ID, AMOUNT))
                .thenReturn(new LoyaltyAccount(CUSTOMER_ID, 2L));
        when(orderService.create(eq(CUSTOMER_ID), eq(PRODUCT_ID), anyInt(), anyDouble()))
                .thenReturn(new Order(1L, CUSTOMER_ID, PRODUCT_ID, 1, AMOUNT, OrderStatus.CONFIRMED));
    }

    @Test
    void happyPathEarnsPointsAndNeverRevokesThem() {
        when(shippingService.generate(PRODUCT_ID))
                .thenReturn(new Shipment(1L, PRODUCT_ID, ShipmentStatus.GENERATED));

        Order order = checkoutUseCase.checkout(CUSTOMER_ID, PRODUCT_ID, 1, AMOUNT);

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(loyaltyService).earnPoints(CUSTOMER_ID, AMOUNT);
        verify(loyaltyService, never()).revokePoints(any(), anyLong());
    }

    @Test
    void shippingFailureRevokesEarnedPoints() {
        when(shippingService.generate(PRODUCT_ID)).thenThrow(new ShippingFailedException(PRODUCT_ID));

        assertThatThrownBy(() -> checkoutUseCase.checkout(CUSTOMER_ID, PRODUCT_ID, 1, AMOUNT))
                .isInstanceOf(ShippingFailedException.class);

        verify(loyaltyService).earnPoints(CUSTOMER_ID, AMOUNT);
        verify(loyaltyService).revokePoints(new LoyaltyAccount(CUSTOMER_ID, 2L), 2L);
    }

    @Test
    void paymentRejectionNeverEarnsPoints() {
        when(paymentService.charge(anyString(), anyDouble())).thenThrow(new PaymentRejectedException(CUSTOMER_ID));

        assertThatThrownBy(() -> checkoutUseCase.checkout(CUSTOMER_ID, PRODUCT_ID, 1, AMOUNT))
                .isInstanceOf(PaymentRejectedException.class);

        verify(loyaltyService, never()).earnPoints(anyString(), anyDouble());
    }
}
