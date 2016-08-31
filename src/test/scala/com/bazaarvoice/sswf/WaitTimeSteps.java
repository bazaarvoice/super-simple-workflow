package com.bazaarvoice.sswf;

public enum WaitTimeSteps implements WorkflowStep {
    DUMMY_STEP(120, 120),
    WAIT_STEP(120, 120);

    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    WaitTimeSteps(final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
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
