package example;

import com.amazonaws.AbortedException;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.model.ActivityTask;
import com.amazonaws.services.simpleworkflow.model.DecisionTask;
import com.amazonaws.services.simpleworkflow.model.RespondActivityTaskCompletedRequest;
import com.amazonaws.services.simpleworkflow.model.RespondDecisionTaskCompletedRequest;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecution;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionInfo;
import com.bazaarvoice.sswf.Builders;
import com.bazaarvoice.sswf.Logger;
import com.bazaarvoice.sswf.model.DefinedStep;
import com.bazaarvoice.sswf.model.history.StepEvent;
import com.bazaarvoice.sswf.model.history.StepsHistory;
import com.bazaarvoice.sswf.service.StepActionWorker;
import com.bazaarvoice.sswf.service.StepDecisionWorker;
import com.bazaarvoice.sswf.service.WorkflowManagement;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ExampleWorkflowService {
    final AmazonSimpleWorkflow swf = new AmazonSimpleWorkflowClient(); // grabs the credentials from your env
    final String domain = "java-sswf-example";
    final String workflow = "example-java-workflow";
    final String workflowVersion = "0.4";
    final String taskList = "my-machine";
    final Logger logger = new StdOutLogger();

    final ExampleWorkflowInput.Parser inputParser = new ExampleWorkflowInput.Parser();
    final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement =
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
            .setLogger(logger)
            .build();

    final ExampleSignalHandler signalHandler = new ExampleSignalHandler(workflowManagement);
    final ExampleWorkflowDefinition workflowDefinition = new ExampleWorkflowDefinition(workflowManagement, signalHandler);

    final StepDecisionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> decisionWorker =
        new Builders.StepDecisionWorkerBuilder<>(ExampleWorkflowInput.class, ExampleWorkflowSteps.class)
            .setDomain(domain)
            .setTaskList(taskList)
            .setSwf(swf)
            .setInputParser(inputParser)
            .setWorkflowDefinition(workflowDefinition)
            .setLogger(logger)
            .build();
    final StepActionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> actionWorker =
        new Builders.StepActionWorkerBuilder<>(ExampleWorkflowInput.class, ExampleWorkflowSteps.class)
            .setDomain(domain)
            .setTaskList(taskList)
            .setSwf(swf)
            .setInputParser(inputParser)
            .setWorkflowDefinition(workflowDefinition)
            .setLogger(logger)
            .build();

    final ScheduledExecutorService decisionExecutor = Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService actionExecutor = Executors.newScheduledThreadPool(3); // We'll let a few actions (from different workflow executions run cuncurrently)

    final ExecutorService actionWorkers = Executors.newCachedThreadPool();

    public void start() {
        workflowManagement.registerWorkflow();

        signalHandler.start();

        decisionExecutor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Decision: polling for work");
                DecisionTask task = decisionWorker.pollForDecisionsToMake();

                while (task != null) {
                    System.out.println("Decision: got task for " + task.getWorkflowExecution());
                    final RespondDecisionTaskCompletedRequest completedRequest = decisionWorker.makeDecision(task);
                    System.out.println("Decision: made decision " + (completedRequest == null ? "null" : completedRequest.getDecisions()));

                    task = decisionWorker.pollForDecisionsToMake();
                }

                System.out.println("Decision: Got no task...");

                System.out.println("Decision: done");
            } catch (AbortedException e) {
                System.out.println("Decision thread shutting down.");
            } catch (Throwable t) { // Make sure this thread can't die silently!
                System.err.println("Decision: unexpected exception. Continuing...");
                t.printStackTrace(System.err);
            }
        }, 5, 5, TimeUnit.SECONDS);

        actionExecutor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Action: polling for work");
                ActivityTask task = actionWorker.pollForWork();
                while (task != null) {
                    final ActivityTask finalTask = task;
                    actionWorkers.submit(() -> {
                        try {
                            System.out.println("Action: got task for " + finalTask.getWorkflowExecution());
                            final RespondActivityTaskCompletedRequest completedRequest = actionWorker.doWork(finalTask);
                            System.out.println("Action: complete for " + finalTask.getWorkflowExecution() + ": " + (completedRequest == null ? "null" : completedRequest.getResult()));
                        } catch (Throwable t) {
                            // This thread isn't important to safeguard, since the executor will make another one next submit().
                            // I'm printing the error for your benefit in the example.
                            System.err.println("Action: unexpected exception executing work.");
                            t.printStackTrace(System.err);
                            throw t;
                        }
                    });
                    task = actionWorker.pollForWork();
                }

                System.out.println("Action: Got no task...");

                System.out.println("Action: done");
            } catch (AbortedException e) {
                System.out.println("Action poller thread shutting down.");
            } catch (Throwable t) { // Make sure this thread can't die silently!
                System.err.println("Action: unexpected exception polling/submitting. Continuing...");
                t.printStackTrace(System.err);
            }
        }, 15, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        decisionExecutor.shutdownNow();
        actionExecutor.shutdownNow();
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
            Thread.sleep(5000);
            final List<WorkflowExecutionInfo> executions = service.workflowManagement.listExecutions(new Date(System.currentTimeMillis() - 1000 * 60 * 60 * 24), new Date());
            int openExecutions = 0;
            for (WorkflowExecutionInfo executionInfo : executions) {
                if (Objects.equals(executionInfo.getExecutionStatus(), "OPEN")) {
                    openExecutions++;

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
            }
            if (openExecutions == 0) {
                System.out.println("all workflows are done!");
                break;
            } else {
                System.out.println(openExecutions + " workflows are still open.");
            }
        }

        final List<WorkflowExecutionInfo> executions = service.workflowManagement.listExecutions(runStart, new Date());
        System.out.println("\nExecutions this run:");
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
