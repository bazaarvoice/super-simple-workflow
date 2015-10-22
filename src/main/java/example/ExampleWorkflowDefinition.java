package example;

import com.bazaarvoice.sswf.WorkflowDefinition;
import com.bazaarvoice.sswf.model.ScheduledStep;
import com.bazaarvoice.sswf.model.history.StepsHistory;
import com.bazaarvoice.sswf.model.result.InProgress;
import com.bazaarvoice.sswf.model.result.StepResult;
import com.bazaarvoice.sswf.model.result.Success;
import scala.Option;
import scala.Some;

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
            new ScheduledStep<>(ExampleWorkflowSteps.EXTRACT_STEP),
            new ScheduledStep<>(ExampleWorkflowSteps.TRANSFORM_STEP, new Some<>("format1->format2")),
            new ScheduledStep<>(ExampleWorkflowSteps.TRANSFORM_STEP, new Some<>("format2->format3")),
            new ScheduledStep<>(ExampleWorkflowSteps.LOAD_STEP)
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

    @Override public StepResult act(final ExampleWorkflowSteps step, final ExampleWorkflowInput exampleWorkflowInput, final Option<String> stepInput) {
        switch (step) {
            case EXTRACT_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "extract finished")) {
                    state.put(exampleWorkflowInput.getName(), "extract finished");
                    return new InProgress("started extract");
                }
                return new Success("Extract is done.");
            case TRANSFORM_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "transform finished (" + stepInput + ")")) {
                    state.put(exampleWorkflowInput.getName(), "transform finished (" + stepInput + ")");
                    return new InProgress("transform started (" + stepInput + ")");
                }
                return new Success("Nothing to do!");
            case LOAD_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "load finished")) {
                    state.put(exampleWorkflowInput.getName(), "load finished");
                    return new InProgress("load started");
                }
                return new Success("load finished");
            default:
                throw new IllegalStateException("Unexpected step enum" + step);
        }
    }
}
