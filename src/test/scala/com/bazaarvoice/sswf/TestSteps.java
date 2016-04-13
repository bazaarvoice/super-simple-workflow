package com.bazaarvoice.sswf;

public enum TestSteps implements WorkflowStep {
    INPROGRESS_STEP(1, 120, 120),
    ;

    private int inProgressTimerSeconds;
    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    TestSteps(final int inProgressTimerSeconds, final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
        this.inProgressTimerSeconds = inProgressTimerSeconds;
        this.startToFinishTimeout = startToFinishTimeout;
        this.startToHeartbeatTimeoutSeconds = startToHeartbeatTimeoutSeconds;
    }

    @Override public int startToFinishTimeoutSeconds() {
        return startToFinishTimeout;
    }

    @Override public int startToHeartbeatTimeoutSeconds() { return startToHeartbeatTimeoutSeconds; }

    @Override public int inProgressTimerSeconds() {
        return inProgressTimerSeconds;
    }


}
