package com.bazaarvoice.sswf

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{InProgress, StepResult, Success}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import example.StdOutLogger
import org.joda.time.Duration
import org.scalatest.FlatSpec

import scala.collection.JavaConversions._

class WaitTimeTestWorkflowDef() extends WorkflowDefinition[String, WaitTimeSteps] {
  private var waitStepInvocationCount: Int = 4
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[WaitTimeSteps]] = List(DefinedStep(WaitTimeSteps.DUMMY_STEP), DefinedStep(WaitTimeSteps.WAIT_STEP), DefinedStep
  (WaitTimeSteps.DUMMY_STEP))
  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = {}
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = {}
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = {}

  override def act(step: WaitTimeSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case WaitTimeSteps.DUMMY_STEP =>
        Thread.sleep(2000)
        Success(None)
      case WaitTimeSteps.WAIT_STEP  => if (waitStepInvocationCount > 0) {
        Thread.sleep(2000)
        waitStepInvocationCount -= 1
        InProgress(Some(s"waiting!!"))
      } else {
        Success(None)
      }
    }
}

class WaitTimeTest extends FlatSpec {

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "wait-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: StdOutLogger = new StdOutLogger
  val manager = new WorkflowManagement[String, WaitTimeSteps](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
  val definition = new WaitTimeTestWorkflowDef()
  val actor = new StepActionWorker[String, WaitTimeSteps](domain, wf, swf, parser, definition, log = logger)
  val decider = new StepDecisionWorker[String, WaitTimeSteps](domain, wf, swf, parser, definition, logger)

  "Each Step" should " have 2 invocations " in {
    manager.registerWorkflow()
    val workflow: WorkflowExecution = manager.startWorkflow("B", "")
    try {

      assert(waitForStepResult(false).getResult === StepResult.serialize(Success(None)))

      //wait step invocations begin...
      assert(waitForStepResult(false).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //2nd invocation for wait step
      assert(waitForStepResult(true).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //3rd invocation for wait step
      assert(waitForStepResult(true).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //4th invocation for wait step
      assert(waitForStepResult(true).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //5th invocation for wait step
      assert(waitForStepResult(true).getResult === StepResult.serialize(Success(None)))

      //final dummy step invocation
      assert(waitForStepResult(false).getResult === StepResult.serialize(Success(None)))

      val wfHistory = manager.describeExecution(workflow.getWorkflowId, workflow.getRunId)
      assert(wfHistory.events.size() == 8)

      val firstDummyStep = wfHistory.events.get(1)
      assert(firstDummyStep.cumulativeActivityTime.toStandardSeconds.getSeconds == 2)
      assert(firstDummyStep.invocations == 1)

      val firstWaitStepInProgress = wfHistory.events.get(2)
      assert(firstWaitStepInProgress.cumulativeActivityTime.toStandardSeconds.getSeconds == 2)
      assert(firstWaitStepInProgress.invocations == 1)

      val secondWaitStepInProgress = wfHistory.events.get(3)
      assert(secondWaitStepInProgress.invocations == 2)

      val firstWaitTime: Duration = new Duration(firstWaitStepInProgress.end.get, secondWaitStepInProgress.start)
      assert(firstWaitTime.getStandardSeconds >= 2 && firstWaitTime.getStandardSeconds <= 3)

      val thirdWaitStepInProgress = wfHistory.events.get(4)
      assert(thirdWaitStepInProgress.invocations == 3)

      val secondWaitTime: Duration = new Duration(secondWaitStepInProgress.end.get, thirdWaitStepInProgress.start)
      assert(secondWaitTime.getStandardSeconds >= 4 && secondWaitTime.getStandardSeconds <= 5)

      val fourthWaitStepInProgress = wfHistory.events.get(5)
      assert(fourthWaitStepInProgress.invocations == 4)

      //wait step caps max wait time at 4 secs
      val thirdWaitTime: Duration = new Duration(thirdWaitStepInProgress.end.get, fourthWaitStepInProgress.start)
      assert(thirdWaitTime.getStandardSeconds >= 4 && thirdWaitTime.getStandardSeconds <= 5)

      val fifthWaitStep = wfHistory.events.get(6)
      assert(fifthWaitStep.invocations == 5)

      val fourthWaitTime: Duration = new Duration(fourthWaitStepInProgress.end.get, fifthWaitStep.start)
      assert(fourthWaitTime.getStandardSeconds >= 4 && fourthWaitTime.getStandardSeconds <= 5)


      val secondDummyStep = wfHistory.events.get(7)
      assert(secondDummyStep.invocations == 1)
      assert(secondDummyStep.cumulativeActivityTime.toStandardSeconds.getSeconds == 2)

    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => () // this means the test passed and the workflow got cancelled
      }
    }

  }

  def waitForStepResult(makeTimerDecision: Boolean): RespondActivityTaskCompletedRequest = {
    if (makeTimerDecision) {
      val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
      val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
      assert(decision.getDecisions.exists((d: Decision) => d.getDecisionType == DecisionType.StartTimer.toString))
    }

    val scheduleActivityDecisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
    val scheduleActivityDecision: RespondDecisionTaskCompletedRequest = decider.makeDecision(scheduleActivityDecisionTask)
    assert(scheduleActivityDecision.getDecisions.exists((d: Decision) => d.getDecisionType == DecisionType.ScheduleActivityTask.toString))

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

