package com.bazaarvoice.sswf

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf.TestUtils.untilNotNull
import com.bazaarvoice.sswf.model.history.StepsHistory
import com.bazaarvoice.sswf.model.result.{StepResult, Success}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._

class InFlightWorkflowChangeWorkflowDefinitionOld() extends WorkflowDefinition[String, InFlightWorkflowChangeStepsOld] {
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[InFlightWorkflowChangeStepsOld]] =
    List[ScheduledStep[InFlightWorkflowChangeStepsOld]](DefinedStep(InFlightWorkflowChangeStepsOld.ONLY_STEP)).asJava

  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsOld], message: String): Unit = ()
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsOld], message: String): Unit = ()
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsOld], message: String): Unit = ()

  override def act(step: InFlightWorkflowChangeStepsOld, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case InFlightWorkflowChangeStepsOld.ONLY_STEP =>
        Success(None)
    }
}

class InFlightWorkflowChangeWorkflowDefinitionNew() extends WorkflowDefinition[String, InFlightWorkflowChangeStepsNew] {
  override def workflow(input: String): _root_.java.util.List[ScheduledStep[InFlightWorkflowChangeStepsNew]] =
    List[ScheduledStep[InFlightWorkflowChangeStepsNew]](DefinedStep(InFlightWorkflowChangeStepsNew.ONLY_STEP)).asJava

  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsNew], message: String): Unit = ()
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsNew], message: String): Unit = ()
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, InFlightWorkflowChangeStepsNew], message: String): Unit = ()

  override def act(step: InFlightWorkflowChangeStepsNew, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult =
    step match {
      case InFlightWorkflowChangeStepsNew.ONLY_STEP =>
        Success(None)
    }
}

class InFlightWorkflowChangeTest extends FlatSpec {
  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }
  private val domain: String = "sswf-tests"
  private val wf: String = "in-flight-wf-change-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: Logger = new SilentLogger

  "a wf registered with steps in one version" should "complete against another (regardless of sort order)" in {
    val managerOld = new WorkflowManagement[String, InFlightWorkflowChangeStepsOld](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)

    val definitionOld = new InFlightWorkflowChangeWorkflowDefinitionOld()
    val actorOld = new StepActionWorker[String, InFlightWorkflowChangeStepsOld](domain, wf, swf, parser, definitionOld, log = logger)
    val deciderOld = new StepDecisionWorker[String, InFlightWorkflowChangeStepsOld](domain, wf, swf, parser, definitionOld, logger)

    managerOld.registerWorkflow()

    val workflow: WorkflowExecution = managerOld.startWorkflow("step-transition-test", "")

    try {
      val scheduleActivityDecisionTask: DecisionTask = untilNotNull(deciderOld.pollForDecisionsToMake())
      deciderOld.makeDecision(scheduleActivityDecisionTask)

      // simulate shutting the app down and restarting it with a new version of the steps

      val managerNew = new WorkflowManagement[String, InFlightWorkflowChangeStepsNew](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)
      val definitionNew = new InFlightWorkflowChangeWorkflowDefinitionNew()
      val actorNew = new StepActionWorker[String, InFlightWorkflowChangeStepsNew](domain, wf, swf, parser, definitionNew, log = logger)
      val deciderNew = new StepDecisionWorker[String, InFlightWorkflowChangeStepsNew](domain, wf, swf, parser, definitionNew, logger)

      val activityTask: ActivityTask = untilNotNull(actorNew.pollForWork())
      val result: String = actorNew.doWork(activityTask).getResult
      assert(result === StepResult.serialize(Success(None)))
    } finally {
      try {
        swf.terminateWorkflowExecution(new TerminateWorkflowExecutionRequest().withDomain(domain).withWorkflowId(workflow.getWorkflowId).withRunId(workflow.getRunId))
      } catch {
        case e: UnknownResourceException => ()
      }
    }
  }
}
