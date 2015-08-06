package example;

import com.bazaarvoice.sswf.WorkflowStep;

public enum ExampleWorkflowSteps implements WorkflowStep {
    EXTRACT_STEP(10, 120),
    TRANSFORM_STEP(10, 120),
    LOAD_STEP(10, 120)
    ;

    private int inProgressTimerSeconds;
    private int startToFinishTimeout;

    ExampleWorkflowSteps(final int inProgressTimerSeconds, final int startToFinishTimeout) {
        this.inProgressTimerSeconds = inProgressTimerSeconds;
        this.startToFinishTimeout = startToFinishTimeout;
    }

    @Override public int startToFinishTimeoutSeconds() {
        return startToFinishTimeout;
    }

    @Override public int inProgressTimerSeconds() {
        return inProgressTimerSeconds;
    }
}
