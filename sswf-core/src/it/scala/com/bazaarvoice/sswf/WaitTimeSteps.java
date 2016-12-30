package com.bazaarvoice.sswf;

public enum WaitTimeSteps implements WorkflowStep {
    DUMMY_STEP(120),
    WAIT_STEP(120);

    private int timeout;

    WaitTimeSteps(final int timeout) {
        this.timeout = timeout;
    }

    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return (invocationNum, cumulativeStepDurationSeconds) -> Math.min(invocationNum * 2, 4);
    }

    @Override public int timeoutSeconds() {
        return timeout;
    }
}
