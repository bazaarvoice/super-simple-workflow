package com.bazaarvoice.sswf.service

import java.util.concurrent.{ScheduledExecutorService, TimeUnit}

import com.amazonaws.AbortedException
import com.amazonaws.services.simpleworkflow.model.{DecisionTask, RespondDecisionTaskCompletedRequest}
import com.bazaarvoice.sswf.WorkflowStep
import com.google.common.util.concurrent.AbstractScheduledService.Scheduler
import com.google.common.util.concurrent.{AbstractScheduledService, MoreExecutors, Service}
import org.slf4j.LoggerFactory

import scala.reflect.ClassTag

class DecisionService[SSWFInput, StepEnum <: (Enum[StepEnum] with WorkflowStep) : ClassTag](stepDecisionWorker: StepDecisionWorker[SSWFInput, StepEnum]) extends AbstractScheduledService with Service {
  private val LOG = LoggerFactory.getLogger(getClass)

  override def scheduler(): Scheduler = Scheduler.newFixedDelaySchedule(5, 5, TimeUnit.SECONDS)

  private case class PollException(cause: Throwable) extends Throwable

  private case class DecisionException(cause: Throwable) extends Throwable

  private def poll(): DecisionTask =
    try {
      stepDecisionWorker.pollForDecisionsToMake()
    } catch {
      case t: Throwable => throw PollException(t)
    }

  private def makeDecision(task: DecisionTask): RespondDecisionTaskCompletedRequest =
    try {
      stepDecisionWorker.makeDecision(task)
    } catch {
      case t: Throwable => throw DecisionException(t)
    }

  override def runOneIteration(): Unit =
    try {
      LOG.info("Polling for work")

      // Don't just poll once; instead, drain the work queue by handling all pending decisions
      var task: DecisionTask = poll()
      while (task != null && isRunning) {
        LOG.info(s"Got task for {}", task.getWorkflowExecution)
        val completedRequest: RespondDecisionTaskCompletedRequest = makeDecision(task)
        LOG.info("Made decision {}", if (completedRequest == null) "null" else completedRequest.getDecisions)
        task = poll()
      }

      LOG.info("Done polling for work.")
    } catch {
      case e: AbortedException =>
        LOG.info("Decision thread shutting down...")
      case PollException(t) =>
        LOG.error("Unexpected exception polling for task. Continuing...", t)
      case DecisionException(t) =>
        LOG.error("Unexpected exception making decision. Continuing...", t)
    }

}
