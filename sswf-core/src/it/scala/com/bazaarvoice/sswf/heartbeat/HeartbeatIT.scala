package com.bazaarvoice.sswf.heartbeat

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflowClient
import com.amazonaws.services.simpleworkflow.model._
import com.bazaarvoice.sswf._
import com.bazaarvoice.sswf.model.history.{StepEvent, StepsHistory}
import com.bazaarvoice.sswf.model.result.{StepResult, Success}
import com.bazaarvoice.sswf.model.{DefinedStep, ScheduledStep, StepInput}
import com.bazaarvoice.sswf.service.except.WorkflowManagementException
import com.bazaarvoice.sswf.service.{StepActionWorker, StepDecisionWorker, WorkflowManagement}
import org.scalatest.FlatSpec

import scala.collection.JavaConverters._

class HeartbeatWorkflowDef(rememberer: Rememberer, beatHeartFor: Long, delayFor: Long) extends WorkflowDefinition[String, HeartbeatStep] {

  override def workflow(input: String): _root_.java.util.List[ScheduledStep[HeartbeatStep]] = List[ScheduledStep[HeartbeatStep]](
    DefinedStep(HeartbeatStep.HEARTBEAT_STEP)
  ).asJava
  override def onFinish(workflowId: String, runId: String, input: String, history: StepsHistory[String, HeartbeatStep], message: String): Unit = rememberer.remember("finished")
  override def onCancel(workflowId: String, runId: String, input: String, history: StepsHistory[String, HeartbeatStep], message: String): Unit = rememberer.remember("cancelled")
  override def onFail(workflowId: String, runId: String, input: String, history: StepsHistory[String, HeartbeatStep], message: String): Unit = rememberer.remember("failed")
  override def act(step: HeartbeatStep, input: String, stepInput: StepInput, heartbeatCallback: HeartbeatCallback, execution: WorkflowExecution): StepResult = {
    var count = 0
    Thread.sleep(delayFor)

    var start = System.currentTimeMillis()
    while ((System.currentTimeMillis() - start) < beatHeartFor) {
      Thread.sleep(1000)
      count = count + 1
      println(s"beat $count times")
      heartbeatCallback.checkIn(s"$count")
    }
    new Success(s"beat $count times")
  }
}

class HeartbeatITest extends FlatSpec {

  val parser = new InputParser[String] {
    override def serialize(input: String): String = input
    override def deserialize(inputString: String): String = inputString
  }

  private val domain: String = "sswf-tests"
  private val wf: String = "heartbeat-test"
  private val swf: AmazonSimpleWorkflowClient = new AmazonSimpleWorkflowClient()
  private val logger: Logger = new SilentLogger
  val manager = new WorkflowManagement[String, HeartbeatStep](domain, wf, "0.0", wf, swf, inputParser = parser, log = logger)

  def actor(definition: HeartbeatWorkflowDef) = new StepActionWorker[String, HeartbeatStep](domain, wf, swf, parser, definition, logger)
  def decider(definition: HeartbeatWorkflowDef) = new StepDecisionWorker[String, HeartbeatStep](domain, wf, swf, parser, definition, logger)

  def untilNotNull[R](action: => R): R = {
    while (true) {
      val r: R = action
      if (r != null) {
        return r
      }
    }
    throw new Exception()
  }

  "a non-beating workflow" should "time out after 11 seconds" in {
    val rememberer = new Rememberer
    manager.registerWorkflow()
    val definition = new HeartbeatWorkflowDef(rememberer, 0, 11000)
    val decider1: StepDecisionWorker[String, HeartbeatStep] = decider(definition)
    val actor1: StepActionWorker[String, HeartbeatStep] = actor(definition)

    val execution: WorkflowExecution = manager.startWorkflow("nonbeat", "")
    try {
      assert({
        val decision: RespondDecisionTaskCompletedRequest = decider1.makeDecision(untilNotNull(decider1.pollForDecisionsToMake()))
        decision.getDecisions.asScala.exists(d => d.getDecisionType == DecisionType.ScheduleActivityTask.toString)
      })

      val start: Long = System.currentTimeMillis()
      assert({
        val work: RespondActivityTaskCompletedRequest = actor1.doWork(untilNotNull(actor1.pollForWork()))
        StepResult.deserialize(work.getResult).isInstanceOf[Success]
      })
      assert((System.currentTimeMillis() - start) >= 11000) // make sure the step really did take "too long"

      assert({
        val decision: RespondDecisionTaskCompletedRequest = decider1.makeDecision(untilNotNull(decider1.pollForDecisionsToMake()))
        // this is consistent with the step timing out.
        decision.getDecisions.asScala.exists(d => d.getDecisionType == DecisionType.ScheduleActivityTask.toString)
      })
    } finally {
      manager.terminateWorkflowExecution(execution.getWorkflowId, execution.getRunId)
    }
  }

  "a beating workflow" should "not time out, even after 15 seconds" in {
    val rememberer = new Rememberer
    manager.registerWorkflow()
    val definition = new HeartbeatWorkflowDef(rememberer, 15000, 0)
    val decider1: StepDecisionWorker[String, HeartbeatStep] = decider(definition)
    val actor1: StepActionWorker[String, HeartbeatStep] = actor(definition)

    val execution: WorkflowExecution = manager.startWorkflow("beat", "")
    try {
      assert({
        decider1
           .makeDecision(untilNotNull(decider1.pollForDecisionsToMake()))
           .getDecisions.asScala
           .exists(d => d.getDecisionType == DecisionType.ScheduleActivityTask.toString)
      })

      val actionStart: Long = System.currentTimeMillis()
      assert(
        StepResult
           .deserialize(actor1.doWork(untilNotNull(actor1.pollForWork())).getResult)
           .isInstanceOf[Success]
      )
      assert((System.currentTimeMillis() - actionStart) >= 15000) // make sure the step really did take "too long"

      assert(
        decider1
           .makeDecision(untilNotNull(decider1.pollForDecisionsToMake()))
           .getDecisions.asScala
           .exists(d => d.getDecisionType == DecisionType.CompleteWorkflowExecution.toString)
      )

      val last: StepEvent[HeartbeatStep] = manager.describeExecution(execution.getWorkflowId, execution.getRunId).events.asScala.last
      assert(last.event.right.get === "WORKFLOW")
      assert(last.result === "SUCCESS")
      
    } finally {
      // workflow should have been terminated by decider.
      assertThrows[WorkflowManagementException](manager.terminateWorkflowExecution(execution.getWorkflowId, execution.getRunId))
    }
  }
}
