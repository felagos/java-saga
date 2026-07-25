package com.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Sits at the com.saga root (not com.saga.checkout), so @SpringBootApplication's default
// component/entity scan already covers the orders/inventory/payments/shipping modules.
@SpringBootApplication
public class CheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
