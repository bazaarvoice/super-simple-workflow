package example;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.model.AmazonSimpleWorkflowException;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionInfo;
import com.bazaarvoice.sswf.Builders;
import com.bazaarvoice.sswf.SLF4JLogger;
import com.bazaarvoice.sswf.model.DefinedStep;
import com.bazaarvoice.sswf.model.history.StepEvent;
import com.bazaarvoice.sswf.model.history.StepsHistory;
import com.bazaarvoice.sswf.service.StepActionWorker;
import com.bazaarvoice.sswf.service.StepDecisionWorker;
import com.bazaarvoice.sswf.service.WorkerServiceFactories;
import com.bazaarvoice.sswf.service.WorkflowManagement;
import com.google.common.util.concurrent.Service;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class ExampleWorkflowService {
    private final AmazonSimpleWorkflow swf = new AmazonSimpleWorkflowClient(); // grabs the credentials from your env
    private final String domain = "java-sswf-example";
    private final String workflow = "example-java-workflow";
    private final String workflowVersion = "0.4";
    private final String taskList = "my-machine";

    private final ExampleWorkflowInput.Parser inputParser = new ExampleWorkflowInput.Parser();
    private final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement =
        new Builders.WorkflowManagementBuilder<>(ExampleWorkflowInput.class, ExampleWorkflowSteps.class)
            .setDomain(domain)
            .setWorkflow(workflow)
            .setWorkflowVersion(workflowVersion)
            .setTaskList(taskList)
            .setSwf(swf)
            .setWorkflowExecutionTimeoutSeconds(60 * 60 * 24 * 30)
            .setWorkflowExecutionRetentionPeriodDays(30)
            .setStepScheduleToStartTimeoutSeconds(30)
            .setInputParser(inputParser)
            .setLogger(new SLF4JLogger(WorkflowManagement.class))
            .build();

    private final ExampleSignalHandler signalHandler = new ExampleSignalHandler(workflowManagement);
    private final ExampleWorkflowDefinition workflowDefinition = new ExampleWorkflowDefinition(workflowManagement, signalHandler);

    private final Service decisionService = WorkerServiceFactories.decisionService(
        new Builders.StepDecisionWorkerBuilder<>(ExampleWorkflowInput.class, ExampleWorkflowSteps.class)
            .setDomain(domain)
            .setTaskList(taskList)
            .setSwf(swf)
            .setInputParser(inputParser)
            .setWorkflowDefinition(workflowDefinition)
            .setLogger(new SLF4JLogger(StepDecisionWorker.class))
            .build(),
        ExampleWorkflowSteps.class
    );


    private final Service actionService = WorkerServiceFactories.actionService(
        new Builders.StepActionWorkerBuilder<>(ExampleWorkflowInput.class, ExampleWorkflowSteps.class)
            .setDomain(domain)
            .setTaskList(taskList)
            .setSwf(swf)
            .setInputParser(inputParser)
            .setWorkflowDefinition(workflowDefinition)
            .setLogger(new SLF4JLogger(StepActionWorker.class))
            .build(),
        3, // max of 3 concurrent actions
        ExampleWorkflowSteps.class);

    private void start() {
        workflowManagement.registerWorkflow();

        signalHandler.start();

        decisionService.startAsync().awaitRunning();

        actionService.startAsync().awaitRunning();
    }

    private void stop() {
        decisionService.stopAsync().awaitTerminated();
        actionService.stopAsync().awaitTerminated();
    }

    public static void main(String[] args) throws InterruptedException {
        final Date runStart = new Date();
        // start all the workers
        final ExampleWorkflowService service = new ExampleWorkflowService();
        service.start();

        // submit a workflow for fun
        startWorkflowForFunAndProfit(service);
        // submit another one for profit
        startWorkflowForFunAndProfit(service);
        // submit a third for profit
        startWorkflowForFunAndProfit(service);

        boolean oneExtractCancelled = false;
        boolean oneNonExtractCancelled = false;

        while (true) {
            try {
                Thread.sleep(5000);
                final List<WorkflowExecutionInfo> executions = service.workflowManagement.listOpenExecutions(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24), new Date());
                for (WorkflowExecutionInfo executionInfo : executions) {
                    final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> execution = service.workflowManagement.describeExecution(executionInfo.getExecution().getWorkflowId(), executionInfo.getExecution().getRunId());
                    System.out.println("");
                    System.out.println("WF history: " + executionInfo.getExecution());
                    System.out.println("  input: " + execution.input());
                    System.out.println("  firedTimers: " + execution.firedTimers());
                    System.out.println("  events:");
                    StepEvent<ExampleWorkflowSteps> lastEvent = null;
                    for (StepEvent<ExampleWorkflowSteps> event : execution.events()) {
                        System.out.println("    " + event);
                        lastEvent = event;
                    }
                    System.out.println();

                    // This simulates an external user deciding to cancel the workflow while a long-running step is in progress.
                    if (lastEvent != null && !oneExtractCancelled &&
                        lastEvent.event().isLeft() && lastEvent.event().left().get() instanceof DefinedStep &&
                        ((DefinedStep<ExampleWorkflowSteps>) lastEvent.event().left().get()).step() == ExampleWorkflowSteps.EXTRACT_STEP && Objects.equals(lastEvent.result(), "STARTED")) {
                        System.out.println("Cancelling workflow!");
                        service.workflowManagement.cancelWorkflowExecution(executionInfo.getExecution().getWorkflowId(), executionInfo.getExecution().getRunId());
                        oneExtractCancelled = true;
                    }

                    // This simulates an external user deciding to cancel the workflow in between steps or during a step that does not respect cancellation requests
                    if (lastEvent != null && !oneNonExtractCancelled &&
                        lastEvent.event().isLeft() && lastEvent.event().left().get() instanceof DefinedStep &&
                        ((DefinedStep<ExampleWorkflowSteps>) lastEvent.event().left().get()).step() != ExampleWorkflowSteps.EXTRACT_STEP) {
                        System.out.println("Cancelling workflow!");
                        service.workflowManagement.cancelWorkflowExecution(executionInfo.getExecution().getWorkflowId(), executionInfo.getExecution().getRunId());
                        oneNonExtractCancelled = true;
                    }
                }
                if (executions.size() == 0) {
                    System.out.println("all workflows are done!");
                    break;
                } else {
                    System.out.println(executions.size() + " workflows are still open.");
                }
            } catch (AmazonSimpleWorkflowException e) {
                if (e.getErrorCode().equals("ThrottlingException")) {
                    System.out.println("Got throttled...");
                    Thread.sleep(10000);
                }
            }
        }

        final List<WorkflowExecutionInfo> executions = service.workflowManagement.listClosedExecutions(runStart, new Date());
        System.out.println("\nExecutions this run:");
        try {
            for (WorkflowExecutionInfo executionInfo : executions) {
                final StepsHistory<ExampleWorkflowInput, ExampleWorkflowSteps> execution = service.workflowManagement.describeExecution(executionInfo.getExecution().getWorkflowId(), executionInfo.getExecution().getRunId());
                System.out.println("");
                System.out.println("WF history: " + executionInfo.getExecution());
                System.out.println("  input: " + execution.input());
                System.out.println("  firedTimers: " + execution.firedTimers());
                System.out.println("  events:");
                for (StepEvent<ExampleWorkflowSteps> event : execution.events()) {
                    System.out.println("    " + event);
                }
                System.out.println();
            }
        } catch (AmazonSimpleWorkflowException e) {
            if (e.getErrorCode().equals("ThrottlingException")) {
                System.out.println("Got throttled...");
            }
        }

        // stop everything and exit
        System.out.println("shutting down...");
        service.stop();
    }

    private static void startWorkflowForFunAndProfit(final ExampleWorkflowService service) {
        final int rando = new Random().nextInt(Integer.MAX_VALUE);
        final ExampleWorkflowInput input = new ExampleWorkflowInput("example-input-parameter-value-" + rando);
        final WorkflowExecution workflowExecution = service.workflowManagement.startWorkflow("workflow-" + rando, input);
        System.out.println("started " + workflowExecution);
    }
}
