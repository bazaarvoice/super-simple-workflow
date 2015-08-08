package example;

import com.bazaarvoice.sswf.WorkflowStep;

public enum ExampleWorkflowSteps implements WorkflowStep {
    EXTRACT_STEP(10, 120),
    TRANSFORM_STEP(10, 120),
    LOAD_STEP(10, 120)
    ;

    private int inProgressTimerSeconds;
    private int startToFinishTimeoutSeconds;

    ExampleWorkflowSteps(final int inProgressTimerSeconds, final int startToFinishTimeoutSeconds) {
        this.inProgressTimerSeconds = inProgressTimerSeconds;
        this.startToFinishTimeoutSeconds = startToFinishTimeoutSeconds;
    }


    @Override public int startToFinishTimeoutSeconds() {
        return startToFinishTimeoutSeconds;
    }

    @Override public int inProgressTimerSeconds() {
        return inProgressTimerSeconds;
    }
}
