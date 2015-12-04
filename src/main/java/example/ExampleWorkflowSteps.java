package example;

import com.bazaarvoice.sswf.WorkflowStep;

public enum ExampleWorkflowSteps implements WorkflowStep {
    EXTRACT_STEP(10, 120, 120),
    TRANSFORM_STEP(10, 120, 120),
    LOAD_STEP(10, 120, 120),
    TIMEOUT_ONCE_STEP(10, 120, 10)
    ;

    private int inProgressTimerSeconds;
    private int startToFinishTimeout;
    private int startToHeartbeatTimeoutSeconds;

    ExampleWorkflowSteps(final int inProgressTimerSeconds, final int startToFinishTimeout, final int startToHeartbeatTimeoutSeconds) {
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
