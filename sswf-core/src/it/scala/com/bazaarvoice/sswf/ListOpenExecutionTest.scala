package com.bazaarvoice.sswf

import java.util.Date

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{StepResult, Success}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.joda.time.DateTime
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._

class DummyWorkflowDefinition() extends WorkflowDefinition[String, ListOpenExecutionTestSteps] {

  override def workflow(input: String): _root_.java.util.List[ScheduledStep[ListOpenExecutionTestSteps]] =
    List[ScheduledStep[ListOpenExecutionTestSteps]](DefinedStep(ListOpenExecutionTestSteps.DUMMY_STEP1), DefinedStep(ListOpenExecutionTestSteps.DUMMY_STEP2)).asJava

  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, ListOpenExecutionTestSteps], message: String): Unit = {}
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, ListOpenExecutionTestSteps], message: String): Unit = {}
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, ListOpenExecutionTestSteps], message: String): Unit = {}

  override def act(step: ListOpenExecutionTestSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case ListOpenExecutionTestSteps.DUMMY_STEP1 => Success(None)
      case ListOpenExecutionTestSteps.DUMMY_STEP2 => Success(None)
    }
}

class ListOpenExecutionTest extends FlatSpec {

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "list-open-executions-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: Logger = new SilentLogger

  val manager = new WorkflowManagement[String, ListOpenExecutionTestSteps](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
  val definition = new DummyWorkflowDefinition()
  val actor = new StepActionWorker[String, ListOpenExecutionTestSteps](domain, wf, swf, parser, definition, log = logger)
  val decider = new StepDecisionWorker[String, ListOpenExecutionTestSteps](domain, wf, swf, parser, definition, logger)

  "list open executions" should " succeed" in {
    manager.registerWorkflow()
    val workflowId = "list-execution-test"
    val startTime = new DateTime().minusSeconds(5).toDate
    val workflow: WorkflowExecution = manager.startWorkflow(workflowId, "")
    try {
      assert(waitForStepResult().getResult === StepResult.serialize(Success(None)))

      val openExecutions = manager.listOpenExecutions(startTime, new Date(), workflowId)
      assert(openExecutions.size() == 1)
      assert(openExecutions.get(0).getExecutionStatus == "OPEN")
      assert(openExecutions.get(0).getExecution.getWorkflowId == workflowId)
      assert(openExecutions.get(0).getStartTimestamp.after(startTime))

      val closedExecutions = manager.listClosedExecutions(startTime, new Date(), workflowId)
      assert(closedExecutions.size() == 0)

      assert(waitForStepResult().getResult === StepResult.serialize(Success(None)))

    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => () // this means the test passed and the workflow got cancelled
      }
    }

    val currentOpenExecutions = manager.listOpenExecutions(startTime, new Date(), workflowId)
    assert(currentOpenExecutions.size() == 0)

    val closedDate = new Date()
    val closedExecutions = manager.listClosedExecutions(startTime, closedDate, workflowId)
    assert(closedExecutions.size() == 1)

    assert(closedExecutions.get(0).getExecutionStatus == "CLOSED")
    assert(closedExecutions.get(0).getExecution.getWorkflowId == workflowId)
    assert(closedExecutions.get(0).getStartTimestamp.after(startTime))
    assert(closedExecutions.get(0).getCloseTimestamp.before(closedDate))

  }
  def waitForStepResult(): RespondActivityTaskCompletedRequest = {
    val scheduleActivityDecisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())

    decider.makeDecision(scheduleActivityDecisionTask)

    val activityTask: ActivityTask = untilNotNull(actor.pollForWork())
    actor.doWork(activityTask)
  }

  def untilNotNull[R](action: => R): R = {
    while (true) {
      val r: R = action
      if (r != null) {
        return r
      }
    }
    throw new Exception()
  }
}

