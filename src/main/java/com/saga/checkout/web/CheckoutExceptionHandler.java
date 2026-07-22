package com.saga.checkout.web;

import com.saga.inventory.domain.InsufficientStockException;
import com.saga.payments.domain.PaymentRejectedException;
import com.saga.shipping.domain.ShippingFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Every domain exception here means the checkout transaction already rolled back (see
 * CheckoutUseCase) — nothing was persisted for this request. Unlike the microservices version,
 * there is no compensation to run: the client gets the failure reason in the same HTTP response.
 */
@RestControllerAdvice
public class CheckoutExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientStock(InsufficientStockException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(PaymentRejectedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handlePaymentRejected(PaymentRejectedException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(ShippingFailedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleShippingFailed(ShippingFailedException e) {
        return new ErrorResponse(e.getMessage());
    }
}
