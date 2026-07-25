package com.saga.checkout.web;

import com.saga.checkout.exceptions.CheckoutInitiationException;
import com.saga.checkout.web.dto.ErrorResponse;
import com.saga.inventory.exceptions.InsufficientStockException;
import com.saga.shared.exceptions.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * InsufficientStock only ever reaches here from the synchronous phase-1 stock check — everything
 * past that point (payment, shipping, order confirmation) is choreographed via NATS listeners, with
 * no HTTP caller left to report to.
 */
@RestControllerAdvice
public class CheckoutExceptionHandler {

    @ExceptionHandler(InsufficientStockException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleInsufficientStock(InsufficientStockException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(NotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler(CheckoutInitiationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleInitiationFailed(CheckoutInitiationException e) {
        return new ErrorResponse(e.getMessage());
    }
}
