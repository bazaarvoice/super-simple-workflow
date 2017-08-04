package com.bazaarvoice.sswf;

public enum InFlightWorkflowChangeStepsOld implements WorkflowStep {
    ONLY_STEP(9999,1);

    private final int versionOverride;
    private final int timeoutSeconds;

    InFlightWorkflowChangeStepsOld(final int versionOverride, final int timeoutSeconds) {

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
