package example;

import com.bazaarvoice.sswf.service.WorkflowManagement;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This class simulates an external process that handles signals.
 * For example, if your workflow sends an email with a link for users to click to confirm receipt,
 * this class is simulating the user.
 */
public class ExampleSignalHandler {
    private Queue<String> signalsToSend = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement;

    public ExampleSignalHandler(final WorkflowManagement<ExampleWorkflowInput, ExampleWorkflowSteps> workflowManagement) {this.workflowManagement = workflowManagement;}

    public void start() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                final String poll = signalsToSend.poll();
                if (poll != null) {
                   workflowManagement.signalWorkflow(poll);
                }
            } catch (Exception e) {
                System.out.println("ERROR: Caught Exception: "+e.toString());
                e.printStackTrace(System.out);
                throw e;
            }
        },10,10, TimeUnit.SECONDS);
    }

    public void stop() {
        executorService.shutdownNow();
    }

    void addSignal(String signal) {
        final boolean offer = signalsToSend.offer(signal);
        if (!offer) {
            System.out.println("ERROR: Couldn't add to queue");
            throw new RuntimeException("Couldn't add to queue");
        }
    }
}
