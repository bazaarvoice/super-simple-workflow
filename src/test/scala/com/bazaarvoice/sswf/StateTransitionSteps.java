package com.bazaarvoice.sswf;


public enum StateTransitionSteps implements WorkflowStep {
    FINAL_STEP(1, 1),
    TIMEOUT_STEP(1, 1);

    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    StateTransitionSteps(final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
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
