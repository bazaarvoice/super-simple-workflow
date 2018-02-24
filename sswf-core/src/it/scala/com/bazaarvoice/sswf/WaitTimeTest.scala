package com.bazaarvoice.sswf

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{InProgress, StepResult, Success}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.joda.time.Duration
import org.scalatest.FlatSpec
import test.utils.StdOutLogger

import scala.collection.JavaConversions._

class WaitTimeTestWorkflowDef() extends WorkflowDefinition[String, WaitTimeSteps] {
  private var waitStepInvocationCount: Int = 4
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[WaitTimeSteps]] = List(DefinedStep(WaitTimeSteps.DUMMY_STEP), DefinedStep(WaitTimeSteps.WAIT_STEP),
    DefinedStep(WaitTimeSteps.DUMMY_STEP), DefinedStep(WaitTimeSteps.DUMMY_STEP))
  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = ()
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = ()
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, WaitTimeSteps], message: String): Unit = ()

  override def act(step: WaitTimeSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case WaitTimeSteps.DUMMY_STEP =>
        Thread.sleep(500)
        Success(None)
      case WaitTimeSteps.WAIT_STEP  => if (waitStepInvocationCount > 0) {
        Thread.sleep(500)
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

      assert(waitForStepResult(makeTimerDecision = false, 0).getResult === StepResult.serialize(Success(None)))

      //wait step invocations begin...
      assert(waitForStepResult(makeTimerDecision = false, 0).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //2nd invocation for wait step
      assert(waitForStepResult(makeTimerDecision = true, 2).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //3rd invocation for wait step
      assert(waitForStepResult(makeTimerDecision = true, 4).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //4th invocation for wait step
      assert(waitForStepResult(makeTimerDecision = true, 4).getResult === StepResult.serialize(InProgress(Some("waiting!!"))))

      //5th invocation for wait step
      assert(waitForStepResult(makeTimerDecision = true, 4).getResult === StepResult.serialize(Success(None)))

      //2nd dummy step invocation
      assert(waitForStepResult(makeTimerDecision = false, 0).getResult === StepResult.serialize(Success(None)))

      //final dummy step invocation
      assert(waitForStepResult(makeTimerDecision = false, 0).getResult === StepResult.serialize(Success(None)))

      val wfHistory = manager.describeExecution(workflow.getWorkflowId, workflow.getRunId)
      assert(wfHistory.events.size() == 9)

      val firstDummyStep = wfHistory.events.get(1)
      assert(firstDummyStep.invocations == 1)
      //case 1: verify cumulative time over single invocation
      assert(firstDummyStep.cumulativeActivityTime.getMillis >= firstDummyStep.end.get.getMillis - firstDummyStep.start.getMillis)

      val firstWaitStepInProgress = wfHistory.events.get(2)
      assert(firstWaitStepInProgress.cumulativeActivityTime.getMillis >= 500)
      assert(firstWaitStepInProgress.invocations == 1)

      val secondWaitStepInProgress = wfHistory.events.get(3)
      assert(secondWaitStepInProgress.invocations == 2)

      val secondWaitStepCumulativeTime: Duration = new Duration(firstWaitStepInProgress.start, secondWaitStepInProgress.end.get)
      assert(secondWaitStepInProgress.cumulativeActivityTime.getMillis >= secondWaitStepCumulativeTime.getMillis)
      assert(secondWaitStepInProgress.cumulativeActivityTime.getMillis >= firstWaitStepInProgress.cumulativeActivityTime.getMillis)

      val thirdWaitStepInProgress = wfHistory.events.get(4)
      assert(thirdWaitStepInProgress.invocations == 3)

      val thirdWaitStepCumulativeTime: Duration = new Duration(firstWaitStepInProgress.start, thirdWaitStepInProgress.end.get)
      assert(thirdWaitStepInProgress.cumulativeActivityTime.getMillis >= thirdWaitStepCumulativeTime.getMillis)
      assert(thirdWaitStepCumulativeTime.getMillis > secondWaitStepCumulativeTime.getMillis)

      val fourthWaitStepInProgress = wfHistory.events.get(5)
      assert(fourthWaitStepInProgress.invocations == 4)

      //case 2: verify cumulative time over two in-progress invocations
      //A  STEP1: InProgress()
      //B  STEP1: Success()      verify: B.cum >= B.end - A.start

      val fifthWaitStep = wfHistory.events.get(6)
      assert(fifthWaitStep.invocations == 5)
      assert(fifthWaitStep.cumulativeActivityTime.getMillis > 0)
      assert(fifthWaitStep.cumulativeActivityTime.getMillis >= new Duration(fourthWaitStepInProgress.start, fifthWaitStep.end.get).getMillis)


      //case 3: verify accumulative time over successive invocations
      //A  STEP0: Success()      verify: A.cum >= A.end - A.start
      //B  STEP0: Success()      verify: B.cum >= B.end - B.start AND B.cum != 0 AND A.cum != 0 AND B.cum < B.end - A.start

      val secondDummyStep = wfHistory.events.get(7)
      val secondDummyStepDuration = new Duration(secondDummyStep.start, secondDummyStep.end.get)
      assert(secondDummyStep.invocations == 1)
      assert(secondDummyStep.cumulativeActivityTime.getMillis > 0)
      assert(secondDummyStep.cumulativeActivityTime.getMillis >= secondDummyStepDuration.getMillis)

      val thirdDummyStep = wfHistory.events.get(8)
      val thirdDummyStepDuration = new Duration(thirdDummyStep.start, thirdDummyStep.end.get)
      assert(thirdDummyStep.invocations == 1)
      assert(thirdDummyStep.cumulativeActivityTime.getMillis > 0)
      assert(thirdDummyStep.cumulativeActivityTime.getMillis >= thirdDummyStepDuration.getMillis)
      assert(thirdDummyStep.cumulativeActivityTime.getMillis < new Duration(secondDummyStep.start, thirdDummyStep.end.get).getMillis)

    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => () // this means the test passed and the workflow got cancelled
      }
    }

  }

  def waitForStepResult(makeTimerDecision: Boolean, waitTimeRequested: Int): RespondActivityTaskCompletedRequest = {
    if (makeTimerDecision) {
      val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
      val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
      assert(decision.getDecisions.exists((d: Decision) => d.getDecisionType == DecisionType.StartTimer.toString))
      assert(decision.getDecisions.exists((d: Decision) => d.getStartTimerDecisionAttributes.getStartToFireTimeout.toInt == waitTimeRequested))
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

