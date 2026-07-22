package com.saga.orchestrator;

public interface SagaStep {

    void execute();

    void compensate();
}
