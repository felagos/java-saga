package com.saga.bff.orchestrator;

public interface SagaStep {

    void execute();

    void compensate();
}
