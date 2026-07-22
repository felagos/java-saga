package com.saga.orchestrator;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the generic saga engine in isolation, independent of any concrete checkout step.
 */
class SagaOrchestratorTest {

    private final SagaOrchestrator orchestrator = new SagaOrchestrator();

    @Test
    void compensatesSucceededStepsInLifoOrderAndSkipsTheFailingAndLaterSteps() {
        SagaStep step1 = mock(SagaStep.class);
        SagaStep step2 = mock(SagaStep.class);
        SagaStep step3 = mock(SagaStep.class);
        RuntimeException failure = new RuntimeException("boom");
        doThrow(failure).when(step3).execute();

        assertThatThrownBy(() -> orchestrator.run(List.of(step1, step2, step3)))
                .isSameAs(failure);

        InOrder order = inOrder(step2, step1);
        order.verify(step2).compensate();
        order.verify(step1).compensate();

        verify(step3, never()).compensate();
    }

    @Test
    void neverStartsStepsAfterTheFailingOne() {
        SagaStep step1 = mock(SagaStep.class);
        SagaStep step2 = mock(SagaStep.class);
        SagaStep step3 = mock(SagaStep.class);
        doThrow(new RuntimeException("boom")).when(step2).execute();

        assertThatThrownBy(() -> orchestrator.run(List.of(step1, step2, step3)));

        verifyNoInteractions(step3);
    }
}
