package com.saga.bff.orchestrator;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Generic in-process saga engine: runs steps in order, and if any step's execute() throws, runs
 * compensate() on the steps that already succeeded, in reverse (LIFO) order. The step that threw
 * is never pushed onto the executed deque, so it never compensates itself.
 */
@Component
public class SagaOrchestrator {

    public void run(List<SagaStep> steps) {
        Deque<SagaStep> executed = new ArrayDeque<>();
        try {
            for (SagaStep step : steps) {
                step.execute();
                executed.push(step);
            }
        } catch (RuntimeException e) {
            executed.forEach(SagaStep::compensate);
            throw e;
        }
    }
}
