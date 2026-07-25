package com.saga.checkout.orchestrator;

public interface SagaStep {

    void execute();

    void compensate();
}
