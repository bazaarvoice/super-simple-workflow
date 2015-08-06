package example;

import com.amazonaws.AbortedException;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient;
import com.amazonaws.services.simpleworkflow.model.ActivityTask;
import com.amazonaws.services.simpleworkflow.model.DecisionTask;
import com.amazonaws.services.simpleworkflow.model.RespondActivityTaskCompletedRequest;
import com.amazonaws.services.simpleworkflow.model.RespondDecisionTaskCompletedRequest;
import com.amazonaws.services.simpleworkflow.model.WorkflowExecutionInfo;
import com.bazaarvoice.sswf.model.StepEvent;
import com.bazaarvoice.sswf.model.StepsHistory;
import com.bazaarvoice.sswf.service.StepActionWorker;
import com.bazaarvoice.sswf.service.StepDecisionWorker;
import com.bazaarvoice.sswf.service.WorkflowExecution;
import com.bazaarvoice.sswf.service.WorkflowManagement;
import scala.reflect.ClassTag$;

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
    final String workflowVersion = "0.3";
    final String taskList = "my-machine";

    final ExampleWorkflowInput.Parser inputParser = new ExampleWorkflowInput.Parser();
    final ExampleWorkflowDefinition stepsDefinition = new ExampleWorkflowDefinition();

    final StepDecisionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> decisionWorker = new StepDecisionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));
    final StepActionWorker<ExampleWorkflowInput, ExampleWorkflowSteps> actionWorker = new StepActionWorker<>(domain, taskList, swf, inputParser, stepsDefinition, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));
    final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement = new WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps>(domain, workflow, workflowVersion, taskList, swf, 60 * 60 * 24 * 30, 30, 30, inputParser, ClassTag$.MODULE$.apply(ExampleWorkflowSteps.class));

    final ScheduledExecutorService decisionExecutor = Executors.newSingleThreadScheduledExecutor();
    final ScheduledExecutorService actionExecutor = Executors.newScheduledThreadPool(3); // We'll let a few actions (from different workflow executions run cuncurrently)

    final ExecutorService actionWorkers = Executors.newCachedThreadPool();

    public void start() {
        workflowManagement.registerWorkflow();

        decisionExecutor.scheduleAtFixedRate(() -> {
            try {
                System.out.println("Decision: polling for work");
                final DecisionTask task = decisionWorker.pollForDecisionsToMake();
                if (task != null) {
                    System.out.println("Decision: got task for " + task.getWorkflowExecution());
                    final RespondDecisionTaskCompletedRequest completedRequest = decisionWorker.makeDecision(task);
                    System.out.println("Decision: made decision " + (completedRequest == null ? "null" : completedRequest.getDecisions()));
                } else {
                    System.out.println("Decision: Got no task...");
                }
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
                final ActivityTask task = actionWorker.pollForWork();
                if (task != null) {
                    actionWorkers.submit(() -> {
                        try {
                            System.out.println("Action: got task for " + task.getWorkflowExecution());
                            final RespondActivityTaskCompletedRequest completedRequest = actionWorker.doWork(task);
                            System.out.println("Action: complete for " + task.getWorkflowExecution() + ": " + (completedRequest == null ? "null" : completedRequest.getResult()));
                        } catch (Throwable t) {
                            // This thread isn't important to safeguard, since the executor will make another one next submit().
                            // I'm printing the error for your benefit in the example.
                            System.err.println("Action: unexpected exception executing work.");
                            t.printStackTrace(System.err);
                            throw t;
                        }
                    });
                } else {
                    System.out.println("Action: Got no task...");
                }
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
        // start all the workers
        final ExampleWorkflowService service = new ExampleWorkflowService();
        service.start();

        // submit a workflow for fun
        startWorkflowForFunAndProfit(service);
        // submit another one for profit
        startWorkflowForFunAndProfit(service);


        while (true) {
            Thread.sleep(10000);
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
                    for (StepEvent<ExampleWorkflowSteps> event : execution.events()) {
                        System.out.println("    " + event);
                    }
                    System.out.println();
                }
            }
            if (openExecutions == 0) {
                System.out.println("all workflows are done!");
                break;
            } else {
                System.out.println(openExecutions + " workflows are still open.");
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
