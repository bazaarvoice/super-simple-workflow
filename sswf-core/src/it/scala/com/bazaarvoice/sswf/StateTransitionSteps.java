package com.bazaarvoice.sswf;


public enum StateTransitionSteps implements WorkflowStep {
    FINAL_STEP(1),
    TIMEOUT_STEP(1);

    private int timeout;

    StateTransitionSteps(final int timeout) {
        this.timeout = timeout;
    }


    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return (invocationNum, cumulativeStepDurationSeconds) -> Math.min(invocationNum * 2, 4);
    }

    @Override public int timeoutSeconds() {
        return timeout;
    }
}
