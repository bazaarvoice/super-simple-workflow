package com.bazaarvoice.sswf;

public enum InFlightWorkflowChangeStepsNew implements WorkflowStep {
    ONLY_STEP(1,1);

    private final int versionOverride;
    private final int timeoutSeconds;

    InFlightWorkflowChangeStepsNew(final int versionOverride, final int timeoutSeconds) {

        this.versionOverride = versionOverride;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override public int timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return null;
    }

    @Override public int versionOverride() {
        return versionOverride;
    }
}
