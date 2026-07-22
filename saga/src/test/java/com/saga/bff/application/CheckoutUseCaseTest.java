package com.saga.bff.application;

import com.saga.inventory.application.InventoryService;
import com.saga.loyalty.application.LoyaltyService;
import com.saga.orchestrator.SagaOrchestrator;
import com.saga.orders.application.OrderService;
import com.saga.orders.domain.Order;
import com.saga.orders.domain.OrderStatus;
import com.saga.payments.application.PaymentService;
import com.saga.payments.domain.PaymentRejectedException;
import com.saga.shipping.application.ShippingService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Wiring test: proves the checkout saga is assembled with the right steps in the right order.
 * The generic LIFO compensation mechanics are covered in isolation by SagaOrchestratorTest.
 */
class CheckoutUseCaseTest {

    private final OrderService orderService = mock(OrderService.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final LoyaltyService loyaltyService = mock(LoyaltyService.class);
    private final ShippingService shippingService = mock(ShippingService.class);

    private final CheckoutUseCase checkoutUseCase = new CheckoutUseCase(orderService, inventoryService,
            paymentService, loyaltyService, shippingService, new SagaOrchestrator());

    @Test
    void paymentRejectionCompensatesReserveAndCreateInLifoOrder() {
        Order pending = new Order(1L, "cust-1", "sku-1", 1, 100.0, OrderStatus.PENDING);
        when(orderService.create("cust-1", "sku-1", 1, 100.0)).thenReturn(pending);
        doThrow(new PaymentRejectedException("cust-1")).when(paymentService).charge("cust-1", 100.0);

        assertThatThrownBy(() -> checkoutUseCase.checkout("cust-1", "sku-1", 1, 100.0))
                .isInstanceOf(PaymentRejectedException.class);

        InOrder order = inOrder(inventoryService, orderService);
        order.verify(inventoryService).release("sku-1", 1);
        order.verify(orderService).cancel(pending);

        verifyNoInteractions(loyaltyService, shippingService);
        verify(orderService, never()).confirm(any());
    }
}
