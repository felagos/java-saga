package com.saga.checkout.web;

import com.saga.checkout.web.dto.ErrorResponseDTO;
import com.saga.inventory.domain.InsufficientStockException;
import com.saga.payments.domain.PaymentRejectedException;
import com.saga.shipping.domain.ShippingFailedException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Each step commits its own local transaction (see CLAUDE.md); a step that throws here has
 * already had SagaOrchestrator run compensation for every step that succeeded before it, so
 * nothing from this request is left applied. The client gets the failure reason in the same
 * HTTP response.
 */
@RestControllerAdvice
public class CheckoutExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDTO handleInsufficientStock(InsufficientStockException e) {
        return new ErrorResponseDTO(e.getMessage());
    }

    @ExceptionHandler(PaymentRejectedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDTO handlePaymentRejected(PaymentRejectedException e) {
        return new ErrorResponseDTO(e.getMessage());
    }

    @ExceptionHandler(ShippingFailedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDTO handleShippingFailed(ShippingFailedException e) {
        return new ErrorResponseDTO(e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponseDTO handleConcurrentUpdate(OptimisticLockingFailureException e) {
        return new ErrorResponseDTO("Concurrent update conflict, please retry");
    }
}
