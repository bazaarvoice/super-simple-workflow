package com.bazaarvoice.sswf;

public enum ListOpenExecutionTestSteps implements WorkflowStep {
    DUMMY_STEP1(1),
    DUMMY_STEP2(1);

    private int timeout;

    ListOpenExecutionTestSteps(final int timeout) {
        this.timeout = timeout;
    }

    @Override public int timeoutSeconds() {
        return timeout;
    }

    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return (invocationNum, cumulativeStepDurationSeconds) -> Math.min(invocationNum * 2, 4);
    }

}
