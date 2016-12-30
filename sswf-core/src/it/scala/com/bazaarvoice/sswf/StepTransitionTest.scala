package com.bazaarvoice.sswf

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{StepResult, Success, TimedOut}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, SleepStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.scalatest.FlatSpec

import scala.collection.JavaConversions._

class StepTransitionWorkflowDefinition() extends WorkflowDefinition[String, StateTransitionSteps] {
  private var timeOutStepInvocationCount: Int = 0

  override def workflow(input: String): _root_.java.util.List[ScheduledStep[StateTransitionSteps]] =
    List(DefinedStep(StateTransitionSteps.TIMEOUT_STEP), SleepStep[StateTransitionSteps](1), SleepStep[StateTransitionSteps](1), DefinedStep(StateTransitionSteps.FINAL_STEP))

  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, StateTransitionSteps], message: String): Unit = ()
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, StateTransitionSteps], message: String): Unit = ()
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, StateTransitionSteps], message: String): Unit = ()

  override def act(step: StateTransitionSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case StateTransitionSteps.FINAL_STEP   =>
        Success(None)
      case StateTransitionSteps.TIMEOUT_STEP => if (timeOutStepInvocationCount < 2) {
        timeOutStepInvocationCount += 1
        TimedOut("test", Some(s"timed out from invocation $timeOutStepInvocationCount"))
      } else {
        Success(None)
      }
    }
}

class StepTransitionTest extends FlatSpec {

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "transition-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: Logger = new SilentLogger
  val manager = new WorkflowManagement[String, StateTransitionSteps](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
  val definition = new StepTransitionWorkflowDefinition()
  val actor = new StepActionWorker[String, StateTransitionSteps](domain, wf, swf, parser, definition, log = logger)
  val decider = new StepDecisionWorker[String, StateTransitionSteps](domain, wf, swf, parser, definition, logger)

  "step transition" should " succeed" in {
    manager.registerWorkflow()
    val workflow: WorkflowExecution = manager.startWorkflow("step-transition-test", "")
    try {
      assert(waitForStepResult().getResult === StepResult.serialize(TimedOut("test", Some("timed out from invocation 1"))))
      assert(waitForStepResult().getResult === StepResult.serialize(TimedOut("test", Some("timed out from invocation 2"))))
      assert(waitForStepResult().getResult === StepResult.serialize(Success(None)))
      makeTimerDecision()
      makeTimerDecision()
      assert(waitForStepResult().getResult === StepResult.serialize(Success(None)))

      val wfHistory = manager.describeExecution(workflow.getWorkflowId, workflow.getRunId)
      assert(wfHistory.events.size() == 7)

      assert(wfHistory.events.get(3).invocations == 3)
      assert(wfHistory.events.get(4).invocations == 1)
      assert(wfHistory.events.get(5).invocations == 1)
      assert(wfHistory.events.get(6).invocations == 1)

    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => () // this means the test passed and the workflow got cancelled
      }
    }

  }

  def makeTimerDecision(): Unit = {
    val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
    val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
    assert(decision.getDecisions.exists((d: Decision) => d.getDecisionType == DecisionType.StartTimer.toString))
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

