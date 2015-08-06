package example;

import com.bazaarvoice.sswf.SSWFStep;
import com.bazaarvoice.sswf.model.StepResult;
import com.bazaarvoice.sswf.model.Success;

public enum ExampleWorkflowSteps implements SSWFStep {
    EXTRACT_STEP("0.0", 10, 120) {
        @Override public ExampleWorkflowSteps next(final Success previousResult) {
            switch (previousResult.message().get()){
                case "NEEDS_EXTRACT": return EXTRACT_STEP;
                case "EXTRACT_IN_PROGRESS": return EXTRACT_STEP;
                case "EXTRACT_DONE": return TRANSFORM_STEP;
                default:throw new RuntimeException(previousResult.message().toString());
            }
        }
    },
    TRANSFORM_STEP("0.0", 10, 120){
        @Override public ExampleWorkflowSteps next(final Success previousResult) {
            switch (previousResult.message().get()){
                case "NO_TRANSFORM_NEEDED": return LOAD_STEP;
                case "TRANSFORM_IN_PROGRESS": return TRANSFORM_STEP;
                case "TRANSFORM_DONE": return LOAD_STEP;
                case "CORRUPT_FILE": return EXTRACT_STEP;
                default:throw new RuntimeException(previousResult.message().toString());
            }
        }
    },
    LOAD_STEP("0.0", 10, 120){
        @Override public ExampleWorkflowSteps next(final Success previousResult) {
            switch (previousResult.message().get()) {
                case "LOAD_IN_PROGRESS": return TRANSFORM_STEP;
                case "LOAD_DONE": return FINISH_WF;
                case "UNRECOVERABLE_ERROR": return FAIL_WF;
                default: throw new RuntimeException(previousResult.message().toString());
            }
        }
    },
    FAIL_WF("0.0", 10, 120) {
        @Override public ExampleWorkflowSteps next(final Success previousResult) {
            return null;
        }
    },
    FINISH_WF("0.0", 10, 120){
        @Override public ExampleWorkflowSteps next(final Success previousResult) {
            return null;
        }
    }
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

    public abstract ExampleWorkflowSteps next(Success previousResult);
}
