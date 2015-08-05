package example;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.model.ActivityTask;
import com.amazonaws.services.simpleworkflow.model.DecisionTask;
import com.bazaarvoice.sswf.model.StepEvent;
import com.bazaarvoice.sswf.model.StepsHistory;
import com.bazaarvoice.sswf.service.StepActionWorker;
import com.bazaarvoice.sswf.service.StepDecisionWorker;
import com.bazaarvoice.sswf.service.WorkflowManagement;
import scala.reflect.ClassTag$;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExampleWorkflowService {
    final AmazonSimpleWorkflow swf = new AmazonSimpleWorkflowClient(); // grabs the credentials from your env
    final String domain = "java-sswf-example";
    final String workflow = "example-java-workflow";
    final String workflowVersion = "0.3";
    final String taskList = "my-machine";

    final ExampleWorkflowInput.Parser inputParser = new ExampleWorkflowInput.Parser();
    final ExampleWorkflowDefinition stepsDefinition = new ExampleWorkflowDefinition();

    final StepDecisionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> decisionWorker = new StepDecisionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));
    final StepActionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> actionWorker = new StepActionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));
    final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement = new WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps>(domain, workflow, workflowVersion, taskList, swf, 60 * 60 * 24 * 30, 30, 60, inputParser, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));

    final ScheduledExecutorService decisionExecutor = Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService actionExecutor = Executors.newScheduledThreadPool(3); // We'll let a few actions (from different workflow executions run cuncurrently)


    public void start() {
        workflowManagement.registerWorkflow();

        decisionExecutor.scheduleWithFixedDelay(() -> {
            final DecisionTask task = decisionWorker.pollForWork();
            if (task != null) {
                System.out.println("Decision: got task");
                decisionWorker.doWork(task);
            } else {
                System.out.println("Decision: Got no task...");
            }
            System.out.println("Decision: done");
        }, 5, 5, TimeUnit.SECONDS);

        actionExecutor.scheduleWithFixedDelay(() -> {
            final ActivityTask task = actionWorker.pollForWork();
            if (task != null) {
                System.out.println("Action: got task");
                actionWorker.doWork(task);
            } else {
                System.out.println("Action: Got no task...");
            }
            System.out.println("Action: done");
        }, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        decisionExecutor.shutdownNow();
        actionExecutor.shutdownNow();
    }

    public static void main(String[] args) throws InterruptedException {
        // start all the workers
        final ExampleWorkflowService service = new ExampleWorkflowService();
        service.start();

        // submit a workflow for fun
        final int rando = new Random().nextInt(Integer.MAX_VALUE);
        final ExampleWorkflowInput input = new ExampleWorkflowInput("example-input-parameter-value-" + rando);
        final WorkflowManagement.WorkflowExecution workflowExecution = service.workflowManagement.startWorkflow("workflow-" + rando, input);

        System.out.println(workflowExecution);

        StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> execution;
        do {
            Thread.sleep(10000);
            execution = service.workflowManagement.describeExecution(workflowExecution.wfId(), workflowExecution.runId());
            System.out.println("");
            System.out.println("WF history:");
            System.out.println("  input: " + execution.input());
            System.out.println("  firedTimers: " + execution.firedTimers());
            System.out.println("  events:");
            for (StepEvent<ExampleWorkflowSteps> event : execution.events()) {
                System.out.println("    " + event);
            }
            System.out.println();
        } while (execution.events().isEmpty() || !eventIsWorkflowFinish(execution.events().get(execution.events().size() - 1)));

        System.out.println("the workflow is done!");
        // stop everything and exit
        service.stop();
        System.exit(0);
    }

    private static boolean eventIsWorkflowFinish(StepEvent<ExampleWorkflowSteps> stepEvent) {
        return stepEvent.event().isRight() && stepEvent.end().isDefined();
    }
}
