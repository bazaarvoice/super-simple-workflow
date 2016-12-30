package com.bazaarvoice.sswf;

public enum TestSteps implements WorkflowStep {
    INPROGRESS_STEP(120) {
        @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
            return (invocationNum, cumulativeStepDurationSeconds) -> 1;
        }
    };

    private int timeout;

    TestSteps(final int timeout) {
        this.timeout = timeout;
    }

    @Override public int timeoutSeconds() {
        return timeout;
    }
}
