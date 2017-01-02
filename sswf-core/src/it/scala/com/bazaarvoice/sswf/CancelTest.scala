package com.bazaarvoice.sswf

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{InProgress, StepResult}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._

class CancelTestWorkflowDef(rememberer: Rememberer) extends WorkflowDefinition[String, TestSteps] {
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[TestSteps]] = List[ScheduledStep[TestSteps]](DefinedStep(TestSteps.INPROGRESS_STEP)).asJava
  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("finished")
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("cancelled")
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, TestSteps], message: String): Unit = rememberer.remember("failed")
  override def act(step: TestSteps, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case TestSteps.INPROGRESS_STEP => InProgress(None)
    }
}

class CancelTest extends FlatSpec {
  val rememberer = new Rememberer

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "cancel-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: Logger = new SilentLogger
  val manager = new WorkflowManagement[String, TestSteps](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
  val definition = new CancelTestWorkflowDef(rememberer)
  val actor = new StepActionWorker[String, TestSteps](domain, wf, swf, parser, definition, log = logger)
  val decider = new StepDecisionWorker[String, TestSteps](domain, wf, swf, parser, definition, logger)

  "InProgress activities" should "get cancelled" in {
    manager.registerWorkflow()
    val workflow: WorkflowExecution = manager.startWorkflow("A", "")
    try {

      {
        val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
        val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
        assert(decision.getDecisions.asScala.exists(d => d.getDecisionType == DecisionType.ScheduleActivityTask.toString))
      }
      {
        val activityTask: ActivityTask = untilNotNull(actor.pollForWork())
        val work: RespondActivityTaskCompletedRequest = actor.doWork(activityTask)
        assert(work.getResult === StepResult.serialize(InProgress(None)))
      }

      manager.cancelWorkflowExecution(workflow.getWorkflowId, workflow.getRunId)

      {
        val decisionTask: DecisionTask = untilNotNull(decider.pollForDecisionsToMake())
        val decision: RespondDecisionTaskCompletedRequest = decider.makeDecision(decisionTask)
        assert(decision.getDecisions.asScala.exists((d: Decision) => d.getDecisionType == DecisionType.CancelWorkflowExecution.toString))
        assert(rememberer.toRemember === "cancelled")
      }
    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => () // this means the test passed and the workflow got cancelled
      }
    }

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
