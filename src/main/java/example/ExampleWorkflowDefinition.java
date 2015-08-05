package example;

import com.bazaarvoice.sswf.WorkflowDefinition;
import com.bazaarvoice.sswf.model.StepResult;
import com.bazaarvoice.sswf.model.StepsHistory;
import com.bazaarvoice.sswf.model.Success;

import java.util.Arrays;
import java.util.List;

public class ExampleWorkflowDefinition implements WorkflowDefinition<ExampleWorkflowInput, ExampleWorkflowSteps> {


    @Override public List<ExampleWorkflowSteps> workflow(final ExampleWorkflowInput exampleWorkflowInput) {
        return Arrays.asList(ExampleWorkflowSteps.EXTRACT_STEP, ExampleWorkflowSteps.TRANSFORM_STEP, ExampleWorkflowSteps.LOAD_STEP);
    }

    @Override public void onFail(final ExampleWorkflowInput exampleWorkflowInput, final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history, final String message) {
        System.out.println("Workflow Failed!!! " + message);
    }

    @Override public void onFinish(final ExampleWorkflowInput exampleWorkflowInput, final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> history, final String message) {
        System.out.println("Workflow Finished!!! " + message);
    }

    @Override public StepResult act(final ExampleWorkflowSteps step, final ExampleWorkflowInput exampleWorkflowInput) {
        switch (step) {
            case EXTRACT_STEP:
                System.out.println("Running extract for "+exampleWorkflowInput.getName());
                return new Success("Nothing to do!");
            case TRANSFORM_STEP:
                System.out.println("Running transform for "+exampleWorkflowInput.getName());
                return new Success("Nothing to do!");
            case LOAD_STEP:
                System.out.println("Running load for "+exampleWorkflowInput.getName());
                return new Success("Nothing to do!");
            default:
                throw new IllegalStateException("Unexpected step enum" + step);
        }
    }
}
