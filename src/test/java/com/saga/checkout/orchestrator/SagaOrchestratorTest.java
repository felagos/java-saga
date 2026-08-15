package com.saga.checkout.orchestrator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SagaOrchestratorTest {

    private final SagaOrchestrator orchestrator = new SagaOrchestrator();

    @Test
    void compensatesSucceededStepsInReverseOrderWhenAStepFails() {
        List<String> compensated = new java.util.ArrayList<>();
        SagaStep first = recordingStep(compensated, "first");
        SagaStep second = recordingStep(compensated, "second");
        SagaStep failing = new SagaStep() {
            @Override
            public void execute() {
                throw new RuntimeException("boom");
            }

            @Override
            public void compensate() {
                throw new AssertionError("must never compensate itself");
            }
        };

        assertThatThrownBy(() -> orchestrator.run(List.of(first, second, failing)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("boom");

        assertThat(compensated).containsExactly("second", "first");
    }

    @Test
    void aFailingCompensationDoesNotStopTheRestFromRunningAndOriginalExceptionSurvives() {
        List<String> compensated = new java.util.ArrayList<>();
        SagaStep first = recordingStep(compensated, "first");
        SagaStep brokenCompensation = new SagaStep() {
            @Override
            public void execute() {
            }

            @Override
            public void compensate() {
                throw new IllegalStateException("compensation failed");
            }
        };
        SagaStep failing = new SagaStep() {
            @Override
            public void execute() {
                throw new RuntimeException("original failure");
            }

            @Override
            public void compensate() {
            }
        };

        assertThatThrownBy(() -> orchestrator.run(List.of(first, brokenCompensation, failing)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("original failure")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(1)
                        .allMatch(s -> s instanceof IllegalStateException));

        assertThat(compensated).containsExactly("first");
    }

    private SagaStep recordingStep(List<String> compensated, String name) {
        return new SagaStep() {
            @Override
            public void execute() {
            }

            @Override
            public void compensate() {
                compensated.add(name);
            }
        };
    }
}
