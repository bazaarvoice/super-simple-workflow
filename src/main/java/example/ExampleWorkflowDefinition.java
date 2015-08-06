package example;

import com.bazaarvoice.sswf.WorkflowDefinition;
import com.bazaarvoice.sswf.model.InProgress;
import com.bazaarvoice.sswf.model.StepResult;
import com.bazaarvoice.sswf.model.StepsHistory;
import com.bazaarvoice.sswf.model.Success;

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

    @Override public List<ExampleWorkflowSteps> workflow(final ExampleWorkflowInput exampleWorkflowInput, final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history) {
        return Arrays.asList(ExampleWorkflowSteps.EXTRACT_STEP, ExampleWorkflowSteps.TRANSFORM_STEP, ExampleWorkflowSteps.LOAD_STEP);
    }

    @Override public void onFail(final ExampleWorkflowInput exampleWorkflowInput, final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history, final String message) {
        System.out.println("Workflow(" + exampleWorkflowInput.getName() + ") Failed!!! " + message);
    }

    @Override public void onFinish(final ExampleWorkflowInput exampleWorkflowInput, final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history, final String message) {
        System.out.println("Workflow(" + exampleWorkflowInput.getName() + ") Finished!!! " + message);
    }

    @Override public StepResult act(final ExampleWorkflowSteps step, final ExampleWorkflowInput exampleWorkflowInput) {
        switch (step) {
            case EXTRACT_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "extract finished")) {
                    state.put(exampleWorkflowInput.getName(), "extract finished");
                    return new InProgress("started extract");
                }
                return new Success("Extract is done.");
            case TRANSFORM_STEP:
                if (!Objects.equals(state.get(exampleWorkflowInput.getName()), "transform finished")) {
                    state.put(exampleWorkflowInput.getName(), "transform finished");
                    return new InProgress("transform started");
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
