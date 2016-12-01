package com.bazaarvoice.sswf;

public enum ListOpenExecutionTestSteps implements WorkflowStep {
    DUMMY_STEP1(1, 1),
    DUMMY_STEP2(1, 1);

    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    ListOpenExecutionTestSteps(final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
        this.startToFinishTimeout = startToFinishTimeout;
        this.startToHeartbeatTimeoutSeconds = startToHeartbeatTimeoutSeconds;
    }

    @Override public int startToFinishTimeoutSeconds() {
        return startToFinishTimeout;
    }

    @Override public int startToHeartbeatTimeoutSeconds() { return startToHeartbeatTimeoutSeconds; }

    @Override public InProgressTimerFunction inProgressTimerSecondsFn() {
        return (invocationNum, cumulativeStepDurationSeconds) -> Math.min(invocationNum * 2, 4);
    }

}
