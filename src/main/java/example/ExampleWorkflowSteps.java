package example;

import com.bazaarvoice.sswf.SSWFStep;

public enum ExampleWorkflowSteps implements SSWFStep {
    EXTRACT_STEP("0.0", 60, 120),
    TRANSFORM_STEP("0.0", 60, 120),
    LOAD_STEP("0.0", 60, 120)
    ;

    private String version;
    private int inProgressTimerSeconds;
    private int startToFinishTimeout;

    ExampleWorkflowSteps(final String version, final int inProgressTimerSeconds, final int startToFinishTimeout) {
        this.version = version;
        this.inProgressTimerSeconds = inProgressTimerSeconds;
        this.startToFinishTimeout = startToFinishTimeout;
    }

    @Override public String version() {
        return version;
    }

    @Override public int startToFinishTimeout() {
        return startToFinishTimeout;
    }

    @Override public int inProgressTimerSeconds() {
        return inProgressTimerSeconds;
    }
}
