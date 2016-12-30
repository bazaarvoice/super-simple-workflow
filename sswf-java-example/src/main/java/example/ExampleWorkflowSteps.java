package example;

import com.bazaarvoice.sswf.ConstantInProgressSleepFunction;
import com.bazaarvoice.sswf.InProgressSleepFunction;
import com.bazaarvoice.sswf.WorkflowStep;

enum ExampleWorkflowSteps implements WorkflowStep {
    EXTRACT_STEP(120, 0/*overridden*/) {
        // here
        @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
            return (invocationNum, cumulativeStepDurationSeconds) -> Math.min(10 * invocationNum, 100);
        }
    },
    TRANSFORM_STEP(120, 10),
    LOAD_STEP(120, 10),
    TIMEOUT_ONCE_STEP(10, 10)
    ;

    private int startToFinishTimeout;
    private final int inProgressSleepSeconds;

    ExampleWorkflowSteps(final int startToFinishTimeout, final int inProgressSleepSeconds) {
        this.startToFinishTimeout = startToFinishTimeout;
        this.inProgressSleepSeconds = inProgressSleepSeconds;
    }

    @Override public int timeoutSeconds() {
        return startToFinishTimeout;
    }

    @Override public InProgressSleepFunction inProgressSleepSecondsFn() {
        return new ConstantInProgressSleepFunction(inProgressSleepSeconds);
    }

}
