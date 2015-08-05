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
import scala.reflect.ClassTag;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExampleWorkflowService {
    final AmazonSimpleWorkflow swf = new AmazonSimpleWorkflowClient(); // grabs the credentials from your env
    final String domain = "java-sswf-example";
    final String workflow = "example-java-workflow";
    final String workflowVersion = "0.1";
    final String taskList = "my-machine";

    final ExampleWorkflowInput.Parser inputParser = new ExampleWorkflowInput.Parser();
    final ExampleWorkflowDefinition stepsDefinition = new ExampleWorkflowDefinition();

    final StepDecisionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> decisionWorker = new StepDecisionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, new ClassTag<>());
    final StepActionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> actionWorker = new StepActionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, new ClassTag<>());
    final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement = new WorkflowManagement<>(domain, workflow, workflowVersion, taskList, swf, 30, 60, inputParser, new ClassTag<>());

    final ScheduledExecutorService decisionExecutor = Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService actionExecutor = Executors.newScheduledThreadPool(3); // We'll let a few actions (from different workflow executions run cuncurrently)


    public void start() {
        workflowManagement.registerWorkflow();

        decisionExecutor.scheduleWithFixedDelay(() -> {
            final DecisionTask task = decisionWorker.pollForWork();
            if (task != null) {
                decisionWorker.doWork(task);
            }
        }, 10, 10, TimeUnit.SECONDS);

        actionExecutor.scheduleWithFixedDelay(() -> {
            final ActivityTask task = actionWorker.pollForWork();
            if (task != null) {
                actionWorker.doWork(task);
            }
        }, 10, 10, TimeUnit.SECONDS);
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
        final WorkflowManagement.WorkflowExecution workflowExecution = service.workflowManagement.startWorkflow("workflow-" + new Random().nextInt(Integer.MAX_VALUE), new ExampleWorkflowInput("example-input-parameter-value"));

        StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> execution;
        do {
            Thread.sleep(1000);
            execution = service.workflowManagement.describeExecution(workflowExecution.wfId(), workflowExecution.runId());
            System.out.println(execution);
        } while (execution.events().isEmpty() || !eventIsWorkflowFinish(execution.events().get(execution.events().size())));

        // stop everything and exit
        service.stop();
    }

    private static boolean eventIsWorkflowFinish(StepEvent<ExampleWorkflowSteps> stepEvent) {
        return stepEvent.event().isRight() && stepEvent.end().isDefined();
    }
}
