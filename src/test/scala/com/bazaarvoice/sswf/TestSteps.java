package com.bazaarvoice.sswf;

public enum TestSteps implements WorkflowStep {
    INPROGRESS_STEP(120, 120) {
        @Override public InProgressTimerFunction inProgressTimerSecondsFn() {
            return (invocationNum, cumulativeStepDurationSeconds) -> 1;
        }
    };

    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    TestSteps(final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
        this.startToFinishTimeout = startToFinishTimeout;
        this.startToHeartbeatTimeoutSeconds = startToHeartbeatTimeoutSeconds;
    }

    @Override public int startToFinishTimeoutSeconds() {
        return startToFinishTimeout;
    }

    @Override public int startToHeartbeatTimeoutSeconds() { return startToHeartbeatTimeoutSeconds; }



}
