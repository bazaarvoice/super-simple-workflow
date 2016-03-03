package example;

import com.bazaarvoice.sswf.HeartbeatCallback;
import com.bazaarvoice.sswf.WorkflowDefinition;
import com.bazaarvoice.sswf.model.DefinedStep;
import com.bazaarvoice.sswf.model.ScheduledStep;
import com.bazaarvoice.sswf.model.SleepStep;
import com.bazaarvoice.sswf.model.StepInput;
import com.bazaarvoice.sswf.model.history.StepsHistory;
import com.bazaarvoice.sswf.model.result.InProgress;
import com.bazaarvoice.sswf.model.result.StepResult;
import com.bazaarvoice.sswf.model.result.Success;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ExampleWorkflowDefinition implements WorkflowDefinition<ExampleWorkflowInput, ExampleWorkflowSteps> {
    // I'm implementing a little state machine here to demonstrate the desired pattern of checking invariants first,
    // returning InProgress until they are done, and returning Success if and only if all invariants are met.
    // This results in a workflow whose steps are idempotent, and which is therefore restartable (and much less headachey).
    private static Map<String, String> state = new ConcurrentHashMap<>();

    @Override public List<ScheduledStep<ExampleWorkflowSteps>> workflow(final ExampleWorkflowInput exampleWorkflowInput) {
        return Arrays.asList(
            new DefinedStep<>(ExampleWorkflowSteps.EXTRACT_STEP),
            new DefinedStep<>(ExampleWorkflowSteps.TRANSFORM_STEP, "format1->format2"),
            new DefinedStep<>(ExampleWorkflowSteps.TRANSFORM_STEP, "format2->format3"),
            new SleepStep<>(60),
            new DefinedStep<>(ExampleWorkflowSteps.TIMEOUT_ONCE_STEP),
            new DefinedStep<>(ExampleWorkflowSteps.LOAD_STEP)
        );
    }

    @Override
    public void onFail(final String workflow,
                       final String run,
                       final ExampleWorkflowInput exampleWorkflowInput,
                       final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history,
                       final String message) {
        System.out.println("[" + workflow + "/" + run + "] Workflow(" + exampleWorkflowInput.getName() + ") Failed!!! " + message);
    }

    @Override
    public void onFinish(final String workflow,
                         final String run,
                         final ExampleWorkflowInput exampleWorkflowInput,
                         final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history,
                         final String message) {
        System.out.println("[" + workflow + "/" + run + "] Workflow(" + exampleWorkflowInput.getName() + ") Finished!!! " + message);
    }

    @Override public StepResult act(final ExampleWorkflowSteps step, final ExampleWorkflowInput exampleWorkflowInput, final StepInput stepInput, final HeartbeatCallback heartbeatCallback) {
        switch (step) {
            case EXTRACT_STEP:
                // Note we're not required to cancel if this is true, it's just a request.
                final boolean cancelRequested = heartbeatCallback.checkIn("just an example heartbeat checkin");
                System.out.println("Cancellation requested: " + cancelRequested);

                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "extract finished")) {
                    state.put(exampleWorkflowInput.getName(), "extract finished");
                    return new InProgress("started extract");
                }
                return new Success("Extract is done.");
            case TRANSFORM_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "transform finished (" + stepInput.stepInputString() + ")")) {
                    state.put(exampleWorkflowInput.getName(), "transform finished (" + stepInput.stepInputString() + ")");
                    return new InProgress("transform started (" + stepInput.stepInputString() + ")");
                }
                return new Success("Nothing to do!");
            case LOAD_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "load finished")) {
                    state.put(exampleWorkflowInput.getName(), "load finished");
                    return new InProgress("load started");
                }
                return new Success("load finished");
            case TIMEOUT_ONCE_STEP:
                System.out.println("resume: " + stepInput.resumeProgress());
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "timeout_once finished")) {
                    state.put(exampleWorkflowInput.getName(), "timeout_once finished");
                    heartbeatCallback.checkIn("[95] of [100]");
                    throw new RuntimeException("Forcing a timeout");
                }
                return new Success("timeout_once finished. Resumed from " + stepInput.resumeProgress());
            default:
                throw new IllegalStateException("Unexpected step enum" + step);
        }
    }
}
